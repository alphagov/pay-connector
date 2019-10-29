package uk.gov.pay.connector.gateway.stripe;

import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.ChargeQueryResponse;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayClientFactory;
import uk.gov.pay.connector.gateway.GatewayException.GatewayConnectionTimeoutException;
import uk.gov.pay.connector.gateway.GatewayException.GatewayErrorException;
import uk.gov.pay.connector.gateway.GatewayException.GenericGatewayException;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.Gateway3DSAuthorisationResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.stripe.handler.StripeAuthoriseHandler;
import uk.gov.pay.connector.gateway.stripe.handler.StripeCancelHandler;
import uk.gov.pay.connector.gateway.stripe.handler.StripeCaptureHandler;
import uk.gov.pay.connector.gateway.stripe.handler.StripeRefundHandler;
import uk.gov.pay.connector.gateway.stripe.json.StripeCharge;
import uk.gov.pay.connector.gateway.stripe.json.StripeErrorResponse;
import uk.gov.pay.connector.gateway.stripe.request.StripeAuthoriseRequest;
import uk.gov.pay.connector.gateway.util.DefaultExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.gateway.util.ExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.util.JsonObjectMapper;
import uk.gov.pay.connector.wallets.WalletAuthorisationGatewayRequest;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

import static javax.ws.rs.core.Response.Status.Family.CLIENT_ERROR;
import static javax.ws.rs.core.Response.Status.Family.SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;

@Singleton
public class StripePaymentProvider implements PaymentProvider {

    private static final Logger logger = LoggerFactory.getLogger(StripePaymentProvider.class);

    private final GatewayClient client;
    private final JsonObjectMapper jsonObjectMapper;
    private final StripeGatewayConfig stripeGatewayConfig;
    private final ExternalRefundAvailabilityCalculator externalRefundAvailabilityCalculator;
    private final StripeCaptureHandler stripeCaptureHandler;
    private final StripeCancelHandler stripeCancelHandler;
    private final StripeRefundHandler stripeRefundHandler;
    private final StripeAuthoriseHandler stripeAuthoriseHandler;

    @Inject
    public StripePaymentProvider(GatewayClientFactory gatewayClientFactory,
                                 ConnectorConfiguration configuration,
                                 JsonObjectMapper jsonObjectMapper,
                                 Environment environment) {
        this.stripeGatewayConfig = configuration.getStripeConfig();
        this.client = gatewayClientFactory.createGatewayClient(STRIPE, environment.metrics());
        this.jsonObjectMapper = jsonObjectMapper;
        this.externalRefundAvailabilityCalculator = new DefaultExternalRefundAvailabilityCalculator();
        stripeCaptureHandler = new StripeCaptureHandler(client, stripeGatewayConfig, jsonObjectMapper);
        stripeCancelHandler = new StripeCancelHandler(client, stripeGatewayConfig);
        stripeRefundHandler = new StripeRefundHandler(client, stripeGatewayConfig, jsonObjectMapper);
        stripeAuthoriseHandler = new StripeAuthoriseHandler(client, stripeGatewayConfig, configuration, jsonObjectMapper);
    }

    @Override
    public PaymentGatewayName getPaymentGatewayName() {
        return STRIPE;
    }

    @Override
    public Optional<String> generateTransactionId() {
        return Optional.empty();
    }

    @Override
    public ChargeQueryResponse queryPaymentStatus(ChargeEntity charge) {
        throw new UnsupportedOperationException("Querying payment status not currently supported by Stripe");
    }

    @Override
    public GatewayResponse authorise(CardAuthorisationGatewayRequest request) {
        return stripeAuthoriseHandler.authorise(request);
    }

    @Override
    public Gateway3DSAuthorisationResponse authorise3dsResponse(Auth3dsResponseGatewayRequest request) {

        if (request.getAuth3DsDetails() != null && request.getAuth3DsDetails().getAuth3DsResult() != null) {

            switch (request.getAuth3DsDetails().getAuth3DsResult()) {
                case CANCELED:
                    return Gateway3DSAuthorisationResponse.of(BaseAuthoriseResponse.AuthoriseStatus.CANCELLED);
                case ERROR:
                    return Gateway3DSAuthorisationResponse.of(BaseAuthoriseResponse.AuthoriseStatus.ERROR);
                case DECLINED:
                    return Gateway3DSAuthorisationResponse.of(BaseAuthoriseResponse.AuthoriseStatus.REJECTED);
                case AUTHORISED:
                    if (request.getTransactionId().get().startsWith("pi_")) {
                        return Gateway3DSAuthorisationResponse.of(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED);
                    }
                    return authorise3DSSource(request);
            }
        }

        // if Auth3DSResult is not available, return response as AUTH_3DS_READY
        // (to keep the transaction in auth waiting until a notification is received from Stripe for 3DS authorisation)
        return Gateway3DSAuthorisationResponse.of(BaseAuthoriseResponse.AuthoriseStatus.AUTH_3DS_READY);
    }

    private Gateway3DSAuthorisationResponse authorise3DSSource(Auth3dsResponseGatewayRequest request) {
        try {
            StripeAuthorisationResponse stripeAuthResponse = createChargeFor3DSSource(request, request.getTransactionId().get());
            return Gateway3DSAuthorisationResponse.of(stripeAuthResponse.authoriseStatus(), stripeAuthResponse.getTransactionId());
        } catch (GatewayErrorException e) {

            if (e.getStatus().isPresent() && e.getStatus().get() == SC_UNAUTHORIZED) {
                return Gateway3DSAuthorisationResponse.of(BaseAuthoriseResponse.AuthoriseStatus.ERROR);
            }

            if (e.getFamily() == SERVER_ERROR) {
                return Gateway3DSAuthorisationResponse.of(BaseAuthoriseResponse.AuthoriseStatus.EXCEPTION);
            }

            if (e.getFamily() == CLIENT_ERROR) {
                StripeErrorResponse stripeErrorResponse = jsonObjectMapper.getObject(e.getResponseFromGateway(), StripeErrorResponse.class);
                logger.info("3DS Authorisation failed for charge {}. Failure code from Stripe: {}, failure message from Stripe: {}. Response code from Stripe: {}",
                        request.getChargeExternalId(), stripeErrorResponse.getError().getCode(), stripeErrorResponse.getError().getMessage(), e.getStatus());

                return Gateway3DSAuthorisationResponse.of(BaseAuthoriseResponse.AuthoriseStatus.REJECTED);
            }

            logger.info("Unrecognised response status when authorising 3DS source. Charge_id={}, status={}, response={}",
                    request.getChargeExternalId(), e.getStatus(), e.getResponseFromGateway());
            throw new RuntimeException("Unrecognised response status when authorising 3DS source.");

        } catch (GatewayConnectionTimeoutException | GenericGatewayException e) {
            return Gateway3DSAuthorisationResponse.of(BaseAuthoriseResponse.AuthoriseStatus.EXCEPTION);
        }
    }

    private StripeAuthorisationResponse createChargeFor3DSSource(Auth3dsResponseGatewayRequest request, String sourceId)
            throws GenericGatewayException, GatewayConnectionTimeoutException, GatewayErrorException {
        String jsonResponse = client.postRequestFor(StripeAuthoriseRequest.of(sourceId, request, stripeGatewayConfig)).getEntity();
        final StripeCharge createChargeResponse = jsonObjectMapper.getObject(jsonResponse, StripeCharge.class);
        return StripeAuthorisationResponse.of(createChargeResponse);
    }

    @Override
    public GatewayResponse<BaseAuthoriseResponse> authoriseWallet(WalletAuthorisationGatewayRequest request) {
        throw new UnsupportedOperationException("Wallets are not supported for Stripe");
    }

    @Override
    public CaptureResponse capture(CaptureGatewayRequest request) {
        return stripeCaptureHandler.capture(request);
    }

    @Override
    public GatewayRefundResponse refund(RefundGatewayRequest request) {
        return stripeRefundHandler.refund(request);
    }

    @Override
    public GatewayResponse<BaseCancelResponse> cancel(CancelGatewayRequest request) {
        return stripeCancelHandler.cancel(request);
    }

    @Override
    public ExternalChargeRefundAvailability getExternalChargeRefundAvailability(ChargeEntity chargeEntity) {
        return externalRefundAvailabilityCalculator.calculate(chargeEntity);
    }
}
