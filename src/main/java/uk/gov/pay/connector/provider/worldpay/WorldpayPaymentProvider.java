package uk.gov.pay.connector.provider.worldpay;

import fj.data.Either;
import io.dropwizard.setup.Environment;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.WorldpayConfig;
import uk.gov.pay.connector.model.CancelGatewayRequest;
import uk.gov.pay.connector.model.CaptureGatewayRequest;
import uk.gov.pay.connector.model.GatewayError;
import uk.gov.pay.connector.model.GatewayRequest;
import uk.gov.pay.connector.model.RefundGatewayRequest;
import uk.gov.pay.connector.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.gateway.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.model.gateway.AuthorisationGatewayRequest;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.service.BaseResponse;
import uk.gov.pay.connector.service.ExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.service.GatewayClient;
import uk.gov.pay.connector.service.GatewayClientFactory;
import uk.gov.pay.connector.service.GatewayOperation;
import uk.gov.pay.connector.service.GatewayOperationClientBuilder;
import uk.gov.pay.connector.service.GatewayOrder;
import uk.gov.pay.connector.service.PaymentGatewayName;
import uk.gov.pay.connector.service.PaymentProviderOperations;
import uk.gov.pay.connector.provider.NotificationConfiguration;
import uk.gov.pay.connector.service.worldpay.WorldpayCancelResponse;
import uk.gov.pay.connector.service.worldpay.WorldpayCaptureResponse;
import uk.gov.pay.connector.service.worldpay.WorldpayOrderStatusResponse;
import uk.gov.pay.connector.service.worldpay.WorldpayRefundResponse;

import javax.inject.Inject;
import javax.ws.rs.client.Invocation.Builder;
import java.util.EnumMap;
import java.util.Optional;
import java.util.function.Function;

import static fj.data.Either.reduce;
import static java.util.UUID.randomUUID;
import static uk.gov.pay.connector.model.domain.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.service.GatewayOperation.AUTHORISE;
import static uk.gov.pay.connector.service.GatewayOperation.CANCEL;
import static uk.gov.pay.connector.service.GatewayOperation.CAPTURE;
import static uk.gov.pay.connector.service.GatewayOperation.REFUND;
import static uk.gov.pay.connector.service.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.service.worldpay.WorldpayOrderRequestBuilder.aWorldpay3dsResponseAuthOrderRequestBuilder;
import static uk.gov.pay.connector.service.worldpay.WorldpayOrderRequestBuilder.aWorldpayAuthoriseOrderRequestBuilder;
import static uk.gov.pay.connector.service.worldpay.WorldpayOrderRequestBuilder.aWorldpayCancelOrderRequestBuilder;
import static uk.gov.pay.connector.service.worldpay.WorldpayOrderRequestBuilder.aWorldpayCaptureOrderRequestBuilder;
import static uk.gov.pay.connector.service.worldpay.WorldpayOrderRequestBuilder.aWorldpayRefundOrderRequestBuilder;

public class WorldpayPaymentProvider implements PaymentProviderOperations {

    public static final String WORLDPAY_MACHINE_COOKIE_NAME = "machine";
    private WorldpayConfig config;
    private GatewayClientFactory gatewayClientFactory;
    private Environment environment;
    private ExternalRefundAvailabilityCalculator externalRefundAvailabilityCalculator;
    private EnumMap<GatewayOperation, GatewayClient> gatewayOperationClientMap;

    @Inject
    public WorldpayPaymentProvider(WorldpayConfig config,
                                   GatewayClientFactory gatewayClientFactory,
                                   ExternalRefundAvailabilityCalculator externalRefundAvailabilityCalculator,
                                   Environment environment) {
        this.config = config;
        this.gatewayClientFactory = gatewayClientFactory;
        this.environment = environment;
        this.gatewayOperationClientMap = GatewayOperationClientBuilder.builder()
                .authClient(gatewayClientForOperation(WORLDPAY, AUTHORISE))
                .cancelClient(gatewayClientForOperation(WORLDPAY, CANCEL))
                .captureClient(gatewayClientForOperation(WORLDPAY, CAPTURE))
                .refundClient(gatewayClientForOperation(WORLDPAY, REFUND))
                .build();
        this.externalRefundAvailabilityCalculator = externalRefundAvailabilityCalculator;
    }

    private GatewayClient gatewayClientForOperation(PaymentGatewayName gateway,
                                                    GatewayOperation operation) {
        return gatewayClientFactory.createGatewayClient(
                gateway, operation, config.getUrls(), WorldpayPaymentProvider::includeSessionIdentifier, environment.metrics()
        );
    }

    @Override
    public PaymentGatewayName getPaymentGatewayName() {
        return PaymentGatewayName.WORLDPAY;
    }

    public Optional<String> generateTransactionId() {
        return Optional.of(randomUUID().toString());
    }

    public GatewayResponse<WorldpayOrderStatusResponse> authorise(AuthorisationGatewayRequest request) {
        return sendReceive(request, buildAuthoriseOrderFor(), WorldpayOrderStatusResponse.class);
    }

    public GatewayResponse<WorldpayOrderStatusResponse> authorise3dsResponse(Auth3dsResponseGatewayRequest request) {
        return sendReceive(request, build3dsResponseAuthOrderFor(), WorldpayOrderStatusResponse.class);
    }

    public GatewayResponse<WorldpayCaptureResponse>  capture(CaptureGatewayRequest request) {
        return this.sendReceive(request, buildCaptureOrderFor(), WorldpayCaptureResponse.class);
    }

    public GatewayResponse<WorldpayRefundResponse> refund(RefundGatewayRequest request) {
        return sendReceive(request, buildRefundOrderFor(), WorldpayRefundResponse.class);
    }

    public GatewayResponse<WorldpayCancelResponse> cancel(CancelGatewayRequest request) {
        return sendReceive(request, buildCancelOrderFor(), WorldpayCancelResponse.class);

    }

    public ExternalChargeRefundAvailability getExternalChargeRefundAvailability(ChargeEntity chargeEntity) {
        return externalRefundAvailabilityCalculator.calculate(chargeEntity);
    }

    public static Builder includeSessionIdentifier(GatewayOrder order, Builder builder) {
        return order.getProviderSessionId()
                .map(sessionId -> builder.cookie(WORLDPAY_MACHINE_COOKIE_NAME, sessionId))
                .orElseGet(() -> builder);
    }

    private Function<AuthorisationGatewayRequest, GatewayOrder> buildAuthoriseOrderFor() {
        return request -> aWorldpayAuthoriseOrderRequestBuilder()
                .withSessionId(request.getChargeExternalId())
                .with3dsRequired(request.getGatewayAccount().isRequires3ds())
                .withTransactionId(request.getTransactionId().orElse(""))
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withDescription(request.getDescription())
                .withAmount(request.getAmount())
                .withAuthorisationDetails(request.getAuthCardDetails())
                .build();
    }

    private Function<Auth3dsResponseGatewayRequest, GatewayOrder> build3dsResponseAuthOrderFor() {
        return request -> aWorldpay3dsResponseAuthOrderRequestBuilder()
                .withPaResponse3ds(request.getAuth3DsDetails().getPaResponse())
                .withSessionId(request.getChargeExternalId())
                .withTransactionId(request.getTransactionId().orElse(""))
                .withProviderSessionId(request.getProviderSessionId().orElse(""))
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .build();
    }

    private Function<CaptureGatewayRequest, GatewayOrder> buildCaptureOrderFor() {
        return request -> aWorldpayCaptureOrderRequestBuilder()
                .withDate(DateTime.now(DateTimeZone.UTC))
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withAmount(request.getAmount())
                .withTransactionId(request.getTransactionId())
                .build();
    }

    private Function<RefundGatewayRequest, GatewayOrder> buildRefundOrderFor() {
        return request -> aWorldpayRefundOrderRequestBuilder()
                .withReference(request.getReference())
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withAmount(request.getAmount())
                .withTransactionId(request.getTransactionId())
                .build();
    }

    private Function<CancelGatewayRequest, GatewayOrder> buildCancelOrderFor() {
        return request -> aWorldpayCancelOrderRequestBuilder()
                .withTransactionId(request.getTransactionId())
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .build();
    }

    private Function<GatewayClient.Response, Optional<String>> extractResponseIdentifier() {
        return response -> Optional.ofNullable(response.getResponseCookies().get(WORLDPAY_MACHINE_COOKIE_NAME));
    }

    private <U extends GatewayRequest,
            T extends BaseResponse>
    GatewayResponse<T> sendReceive(
            U request,
            Function<U, GatewayOrder> order,
            Class<T> clazz
    ) {

        final GatewayClient gatewayClient = gatewayClientFor(request);
        final Either<GatewayError, GatewayClient.Response> result = gatewayClient
                .postRequestFor(null, request.getGatewayAccount(), order.apply(request));

        return reduce(result.bimap(
                GatewayResponse::with,
                r -> mapToResponse(r, clazz, gatewayClient)
            ));
    }

    private <U extends GatewayRequest> GatewayClient gatewayClientFor(U request) {
        return gatewayOperationClientMap.get(request.getRequestType());
    }

    private <T extends BaseResponse> GatewayResponse<T> mapToResponse(GatewayClient.Response response,
                                          Class<T> clazz,
                                          GatewayClient client) {
        GatewayResponse.GatewayResponseBuilder<T> responseBuilder = GatewayResponse.GatewayResponseBuilder.responseBuilder();

        reduce(
                client.unmarshallResponse(response, clazz)
                        .bimap(
                                responseBuilder::withGatewayError,
                                responseBuilder::withResponse
                        )
        );

        final String cookie = response.getResponseCookies().get(WORLDPAY_MACHINE_COOKIE_NAME);
        if (cookie != null) {
            responseBuilder.withSessionIdentifier(cookie);
        }

        return responseBuilder.build();

    }
}
