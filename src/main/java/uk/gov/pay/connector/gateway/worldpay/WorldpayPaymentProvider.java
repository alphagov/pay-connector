package uk.gov.pay.connector.gateway.worldpay;

import io.dropwizard.setup.Environment;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayClientFactory;
import uk.gov.pay.connector.gateway.GatewayErrors.GatewayConnectionErrorException;
import uk.gov.pay.connector.gateway.GatewayErrors.GatewayConnectionTimeoutErrorException;
import uk.gov.pay.connector.gateway.GatewayErrors.GenericGatewayErrorException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.epdq.ChargeQueryResponse;
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
import uk.gov.pay.connector.gateway.util.DefaultExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.gateway.util.ExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.gateway.worldpay.applepay.WorldpayWalletAuthorisationHandler;
import uk.gov.pay.connector.wallets.WalletAuthorisationGatewayRequest;

import javax.inject.Inject;
import javax.ws.rs.client.Invocation.Builder;
import java.util.Optional;
import java.util.function.BiFunction;

import static java.util.UUID.randomUUID;
import static uk.gov.pay.connector.gateway.GatewayOperation.AUTHORISE;
import static uk.gov.pay.connector.gateway.GatewayOperation.CANCEL;
import static uk.gov.pay.connector.gateway.GatewayOperation.CAPTURE;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpay3dsResponseAuthOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayAuthoriseOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayCancelOrderRequestBuilder;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;

public class WorldpayPaymentProvider implements PaymentProvider, WorldpayGatewayResponseGenerator {

    public static final String WORLDPAY_MACHINE_COOKIE_NAME = "machine";

    private final GatewayClient authoriseClient;
    private final GatewayClient cancelClient;
    private final GatewayClient captureClient;
    private final ExternalRefundAvailabilityCalculator externalRefundAvailabilityCalculator;

    private final WorldpayCaptureHandler worldpayCaptureHandler;
    private final WorldpayRefundHandler worldpayRefundHandler;
    private final WorldpayWalletAuthorisationHandler worldpayWalletAuthorisationHandler;

    @Inject
    public WorldpayPaymentProvider(ConnectorConfiguration configuration,
                                   GatewayClientFactory gatewayClientFactory,
                                   Environment environment) {
        authoriseClient = gatewayClientFactory.createGatewayClient(WORLDPAY, AUTHORISE, configuration.getGatewayConfigFor(WORLDPAY).getUrls(), includeSessionIdentifier(), environment.metrics());
        cancelClient = gatewayClientFactory.createGatewayClient(WORLDPAY, CANCEL, configuration.getGatewayConfigFor(WORLDPAY).getUrls(), includeSessionIdentifier(), environment.metrics());
        captureClient = gatewayClientFactory.createGatewayClient(WORLDPAY, CAPTURE, configuration.getGatewayConfigFor(WORLDPAY).getUrls(), includeSessionIdentifier(), environment.metrics());
        externalRefundAvailabilityCalculator = new DefaultExternalRefundAvailabilityCalculator();
        worldpayCaptureHandler = new WorldpayCaptureHandler(captureClient);
        worldpayRefundHandler = new WorldpayRefundHandler(captureClient);
        worldpayWalletAuthorisationHandler = new WorldpayWalletAuthorisationHandler(authoriseClient);
    }

    @Override
    public PaymentGatewayName getPaymentGatewayName() {
        return WORLDPAY;
    }

    @Override
    public Optional<String> generateTransactionId() {
        return Optional.of(randomUUID().toString());
    }

    @Override
    public ChargeQueryResponse queryPaymentStatus(ChargeEntity charge) {
        throw new UnsupportedOperationException("Querying payment status not currently supported by Worldpay");
    }
    
    @Override
    public GatewayResponse<BaseAuthoriseResponse> authorise(CardAuthorisationGatewayRequest request) 
            throws GenericGatewayErrorException, GatewayConnectionErrorException, GatewayConnectionTimeoutErrorException {
        GatewayClient.Response response = authoriseClient.postRequestFor(null, request.getGatewayAccount(), buildAuthoriseOrder(request));
        return getWorldpayGatewayResponse(response);
    }

    @Override
    public Gateway3DSAuthorisationResponse authorise3dsResponse(Auth3dsResponseGatewayRequest request) {
        try {
            GatewayClient.Response response = authoriseClient.postRequestFor(null, request.getGatewayAccount(), build3dsResponseAuthOrder(request));
            GatewayResponse<BaseAuthoriseResponse> gatewayResponse = getWorldpayGatewayResponse(response);
            BaseAuthoriseResponse authoriseResponse = gatewayResponse.getBaseResponse().get();
            return Gateway3DSAuthorisationResponse.of(gatewayResponse.toString(), authoriseResponse.authoriseStatus(), authoriseResponse.getTransactionId());
        } catch (GenericGatewayErrorException | GatewayConnectionTimeoutErrorException | GatewayConnectionErrorException e) {
            return Gateway3DSAuthorisationResponse.of(e.getMessage(), BaseAuthoriseResponse.AuthoriseStatus.EXCEPTION);
        }
    }

    @Override
    public CaptureResponse capture(CaptureGatewayRequest request) {
        return worldpayCaptureHandler.capture(request);
    }

    @Override
    public GatewayResponse<BaseAuthoriseResponse> authoriseWallet(WalletAuthorisationGatewayRequest request) 
            throws GenericGatewayErrorException, GatewayConnectionErrorException, GatewayConnectionTimeoutErrorException {
        return worldpayWalletAuthorisationHandler.authorise(request);
    }

    @Override
    public GatewayRefundResponse refund(RefundGatewayRequest request) {
        return worldpayRefundHandler.refund(request);
    }

    @Override
    public GatewayResponse<BaseCancelResponse> cancel(CancelGatewayRequest request) throws GenericGatewayErrorException, GatewayConnectionErrorException, GatewayConnectionTimeoutErrorException {
        GatewayClient.Response response = cancelClient.postRequestFor(null, request.getGatewayAccount(), buildCancelOrder(request));
        return getWorldpayGatewayResponse(response);
    }

    @Override
    public ExternalChargeRefundAvailability getExternalChargeRefundAvailability(ChargeEntity chargeEntity) {
        return externalRefundAvailabilityCalculator.calculate(chargeEntity);
    }

    public static BiFunction<GatewayOrder, Builder, Builder> includeSessionIdentifier() {
        return (order, builder) ->
                order.getProviderSessionId()
                        .map(sessionId -> builder.cookie(WORLDPAY_MACHINE_COOKIE_NAME, sessionId))
                        .orElse(builder);
    }

    private GatewayOrder buildAuthoriseOrder(CardAuthorisationGatewayRequest request) {
        return aWorldpayAuthoriseOrderRequestBuilder()
                .withSessionId(request.getChargeExternalId())
                .with3dsRequired(request.getGatewayAccount().isRequires3ds())
                .withTransactionId(request.getTransactionId().orElse(""))
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withDescription(request.getDescription())
                .withAmount(request.getAmount())
                .withAuthorisationDetails(request.getAuthCardDetails())
                .build();
    }

    private GatewayOrder build3dsResponseAuthOrder(Auth3dsResponseGatewayRequest request) {
        return aWorldpay3dsResponseAuthOrderRequestBuilder()
                .withPaResponse3ds(request.getAuth3DsDetails().getPaResponse())
                .withSessionId(request.getChargeExternalId())
                .withTransactionId(request.getTransactionId().orElse(""))
                .withProviderSessionId(request.getProviderSessionId().orElse(""))
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .build();
    }

    private GatewayOrder buildCancelOrder(CancelGatewayRequest request) {
        return aWorldpayCancelOrderRequestBuilder()
                .withTransactionId(request.getTransactionId())
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .build();
    }
}
