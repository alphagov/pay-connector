package uk.gov.pay.connector.gateway.worldpay;

import fj.data.Either;
import io.dropwizard.setup.Environment;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.applepay.ApplePayAuthorisationGatewayRequest;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayClientFactory;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.BaseRefundResponse;
import uk.gov.pay.connector.gateway.model.response.Gateway3DSAuthorisationResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.util.DefaultExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.gateway.util.ExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.gateway.util.GatewayResponseGenerator;
import uk.gov.pay.connector.gateway.worldpay.applepay.WorldpayApplePayAuthorisationHandler;

import javax.inject.Inject;
import javax.ws.rs.client.Invocation.Builder;
import java.util.Optional;
import java.util.function.BiFunction;

import static java.util.UUID.randomUUID;
import static uk.gov.pay.connector.gateway.GatewayOperation.AUTHORISE;
import static uk.gov.pay.connector.gateway.GatewayOperation.CANCEL;
import static uk.gov.pay.connector.gateway.GatewayOperation.CAPTURE;
import static uk.gov.pay.connector.gateway.GatewayOperation.REFUND;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpay3dsResponseAuthOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayAuthoriseOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayCancelOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayRefundOrderRequestBuilder;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;

public class WorldpayPaymentProvider implements PaymentProvider {

    public static final String WORLDPAY_MACHINE_COOKIE_NAME = "machine";

    private final GatewayClient authoriseClient;
    private final GatewayClient cancelClient;
    private final GatewayClient captureClient;
    private final GatewayClient refundClient;
    private final ExternalRefundAvailabilityCalculator externalRefundAvailabilityCalculator;

    private final WorldpayCaptureHandler worldpayCaptureHandler;
    private final WorldpayApplePayAuthorisationHandler worldpayApplePayAuthorisationHandler;

    @Inject
    public WorldpayPaymentProvider(ConnectorConfiguration configuration,
                                   GatewayClientFactory gatewayClientFactory,
                                   Environment environment) {
        authoriseClient = gatewayClientFactory.createGatewayClient(WORLDPAY, AUTHORISE, configuration.getGatewayConfigFor(WORLDPAY).getUrls(), includeSessionIdentifier(), environment.metrics());
        cancelClient = gatewayClientFactory.createGatewayClient(WORLDPAY, CANCEL, configuration.getGatewayConfigFor(WORLDPAY).getUrls(), includeSessionIdentifier(), environment.metrics());
        captureClient = gatewayClientFactory.createGatewayClient(WORLDPAY, CAPTURE, configuration.getGatewayConfigFor(WORLDPAY).getUrls(), includeSessionIdentifier(), environment.metrics());
        refundClient = gatewayClientFactory.createGatewayClient(WORLDPAY, REFUND, configuration.getGatewayConfigFor(WORLDPAY).getUrls(), includeSessionIdentifier(), environment.metrics());
        externalRefundAvailabilityCalculator = new DefaultExternalRefundAvailabilityCalculator();
        worldpayCaptureHandler = new WorldpayCaptureHandler(captureClient);
        worldpayApplePayAuthorisationHandler = new WorldpayApplePayAuthorisationHandler(authoriseClient);
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
    public GatewayResponse<BaseAuthoriseResponse> authorise(CardAuthorisationGatewayRequest request) {
        Either<GatewayError, GatewayClient.Response> response = authoriseClient.postRequestFor(null, request.getGatewayAccount(), buildAuthoriseOrder(request));
        return GatewayResponseGenerator.getWorldpayGatewayResponse(authoriseClient, response, WorldpayOrderStatusResponse.class);
    }

    @Override
    public Gateway3DSAuthorisationResponse authorise3dsResponse(Auth3dsResponseGatewayRequest request) {
        Either<GatewayError, GatewayClient.Response> response = authoriseClient.postRequestFor(null, request.getGatewayAccount(), build3dsResponseAuthOrder(request));
        GatewayResponse<BaseAuthoriseResponse> gatewayResponse = GatewayResponseGenerator.getWorldpayGatewayResponse(authoriseClient, response, WorldpayOrderStatusResponse.class);

        return gatewayResponse.getBaseResponse().map(
                baseResponse -> Gateway3DSAuthorisationResponse.of(gatewayResponse.toString(), baseResponse.authoriseStatus(), baseResponse.getTransactionId()))
                .orElseGet(() -> Gateway3DSAuthorisationResponse.of(gatewayResponse.toString(), BaseAuthoriseResponse.AuthoriseStatus.EXCEPTION));
    }

    @Override
    public CaptureResponse capture(CaptureGatewayRequest request) {
        return worldpayCaptureHandler.capture(request);
    }

    @Override
    public GatewayResponse<BaseAuthoriseResponse> authoriseApplePay(ApplePayAuthorisationGatewayRequest request) {
        return worldpayApplePayAuthorisationHandler.authorise(request);
    }

    @Override
    public GatewayResponse<BaseRefundResponse> refund(RefundGatewayRequest request) {
        Either<GatewayError, GatewayClient.Response> response = refundClient.postRequestFor(null, request.getGatewayAccount(), buildRefundOrder(request));
        return GatewayResponseGenerator.getWorldpayGatewayResponse(refundClient, response, WorldpayRefundResponse.class);
    }

    @Override
    public GatewayResponse<BaseCancelResponse> cancel(CancelGatewayRequest request) {
        Either<GatewayError, GatewayClient.Response> response = cancelClient.postRequestFor(null, request.getGatewayAccount(), buildCancelOrder(request));
        return GatewayResponseGenerator.getWorldpayGatewayResponse(cancelClient, response, WorldpayCancelResponse.class);
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

    private GatewayOrder buildRefundOrder(RefundGatewayRequest request) {
        return aWorldpayRefundOrderRequestBuilder()
                .withReference(request.getReference())
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withAmount(request.getAmount())
                .withTransactionId(request.getTransactionId())
                .build();
    }

    private GatewayOrder buildCancelOrder(CancelGatewayRequest request) {
        return aWorldpayCancelOrderRequestBuilder()
                .withTransactionId(request.getTransactionId())
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .build();
    }
}
