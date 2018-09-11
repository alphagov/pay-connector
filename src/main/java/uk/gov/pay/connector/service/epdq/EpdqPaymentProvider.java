package uk.gov.pay.connector.service.epdq;

import com.codahale.metrics.MetricRegistry;
import fj.data.Either;
import org.apache.http.NameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.model.CancelGatewayRequest;
import uk.gov.pay.connector.model.CaptureGatewayRequest;
import uk.gov.pay.connector.model.GatewayError;
import uk.gov.pay.connector.model.Notification;
import uk.gov.pay.connector.model.Notifications;
import uk.gov.pay.connector.model.RefundGatewayRequest;
import uk.gov.pay.connector.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.model.domain.Auth3dsDetails;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.gateway.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.model.gateway.AuthorisationGatewayRequest;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.service.BaseAuthoriseResponse;
import uk.gov.pay.connector.service.BasePaymentProvider;
import uk.gov.pay.connector.service.BaseResponse;
import uk.gov.pay.connector.service.ExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.service.GatewayClient;
import uk.gov.pay.connector.service.GatewayOperation;
import uk.gov.pay.connector.service.GatewayOrder;
import uk.gov.pay.connector.service.PaymentGatewayName;
import uk.gov.pay.connector.service.StatusMapper;

import javax.ws.rs.client.Invocation;
import java.nio.charset.Charset;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import static fj.data.Either.left;
import static fj.data.Either.right;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static uk.gov.pay.connector.model.domain.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.model.domain.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.model.domain.GatewayAccount.CREDENTIALS_SHA_IN_PASSPHRASE;
import static uk.gov.pay.connector.model.domain.GatewayAccount.CREDENTIALS_SHA_OUT_PASSPHRASE;
import static uk.gov.pay.connector.model.domain.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.service.BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED;
import static uk.gov.pay.connector.service.BaseAuthoriseResponse.AuthoriseStatus.ERROR;
import static uk.gov.pay.connector.service.BaseAuthoriseResponse.AuthoriseStatus.REJECTED;
import static uk.gov.pay.connector.service.epdq.EpdqNotification.SHASIGN_KEY;
import static uk.gov.pay.connector.service.epdq.EpdqOrderRequestBuilder.anEpdq3DsAuthoriseOrderRequestBuilder;
import static uk.gov.pay.connector.service.epdq.EpdqOrderRequestBuilder.anEpdqAuthoriseOrderRequestBuilder;
import static uk.gov.pay.connector.service.epdq.EpdqOrderRequestBuilder.anEpdqCancelOrderRequestBuilder;
import static uk.gov.pay.connector.service.epdq.EpdqOrderRequestBuilder.anEpdqCaptureOrderRequestBuilder;
import static uk.gov.pay.connector.service.epdq.EpdqOrderRequestBuilder.anEpdqQueryOrderRequestBuilder;
import static uk.gov.pay.connector.service.epdq.EpdqOrderRequestBuilder.anEpdqRefundOrderRequestBuilder;

public class EpdqPaymentProvider extends BasePaymentProvider<BaseResponse, String> {

    private static final Logger LOGGER = LoggerFactory.getLogger(EpdqPaymentProvider.class);

    public static final String ROUTE_FOR_NEW_ORDER = "orderdirect.asp";
    public static final String ROUTE_FOR_MAINTENANCE_ORDER = "maintenancedirect.asp";
    public static final String ROUTE_FOR_QUERY_ORDER = "querydirect.asp";

    /**
     * ePDQ have never confirmed that they use Windows-1252 to decode
     * application/x-www-form-urlencoded payloads sent by us to them and use
     * Windows-1252 to encode application/x-www-form-urlencoded notification
     * payloads sent from them to us but experimentation — and specifically the
     * fact that ’ (that’s U+2019 right single quotation mark in Unicode
     * parlance) seems to encode to %92 — makes us believe that they do
     */
    static final Charset EPDQ_APPLICATION_X_WWW_FORM_URLENCODED_CHARSET = Charset.forName("windows-1252");

    private final SignatureGenerator signatureGenerator;
    private final String frontendUrl;
    private final MetricRegistry metricRegistry;

    public EpdqPaymentProvider(EnumMap<GatewayOperation, GatewayClient> clients, SignatureGenerator signatureGenerator,
                               ExternalRefundAvailabilityCalculator externalRefundAvailabilityCalculator, String frontendUrl, MetricRegistry metricRegistry) {
        super(clients, externalRefundAvailabilityCalculator);
        this.signatureGenerator = signatureGenerator;
        this.frontendUrl = frontendUrl;
        this.metricRegistry = metricRegistry;
    }

    @Override
    public PaymentGatewayName getPaymentGatewayName() {
        return PaymentGatewayName.EPDQ;
    }

    @Override
    public Optional<String> generateTransactionId() {
        return Optional.empty();
    }

    @Override
    public GatewayResponse authorise(AuthorisationGatewayRequest request) {
        return sendReceive(ROUTE_FOR_NEW_ORDER, request, buildAuthoriseOrderFor(frontendUrl), EpdqAuthorisationResponse.class, extractResponseIdentifier());
    }

    @Override
    public GatewayResponse capture(CaptureGatewayRequest request) {
        return sendReceive(ROUTE_FOR_MAINTENANCE_ORDER, request, buildCaptureOrderFor(), EpdqCaptureResponse.class, extractResponseIdentifier());
    }

    @Override
    public GatewayResponse refund(RefundGatewayRequest request) {
        return sendReceive(ROUTE_FOR_MAINTENANCE_ORDER, request, buildRefundOrderFor(), EpdqRefundResponse.class, extractResponseIdentifier());
    }

    @Override
    public GatewayResponse cancel(CancelGatewayRequest request) {
        return sendReceive(ROUTE_FOR_MAINTENANCE_ORDER, request, buildCancelOrderFor(), EpdqCancelResponse.class, extractResponseIdentifier());
    }

    @Override
    public GatewayResponse authorise3dsResponse(Auth3dsResponseGatewayRequest request) {
        GatewayResponse<EpdqAuthorisationResponse> gatewayResponse = sendReceive(ROUTE_FOR_QUERY_ORDER, request, buildQueryOrderRequestFor(), EpdqAuthorisationResponse.class, extractResponseIdentifier());

        BaseAuthoriseResponse.AuthoriseStatus authoriseStatus = gatewayResponse.getBaseResponse().map(epdqStatus -> epdqStatus.authoriseStatus()).orElse(ERROR);
        final Auth3dsDetails.Auth3dsResult auth3DResult = request.getAuth3DsDetails().getAuth3DsResult() == null ?
                Auth3dsDetails.Auth3dsResult.ERROR : // we treat no result from frontend as an error
                request.getAuth3DsDetails().getAuth3DsResult();

        if (responseDoesNotMatchWithUserResult(authoriseStatus, auth3DResult)) {
            LOGGER.warn("epdq.authorise-3ds.result.mismatch for chargeId={}, frontendstatus={}, gatewaystatus={}",
                    request.getChargeExternalId(), request.getGatewayAccount().getId(), auth3DResult, authoriseStatus);
            metricRegistry.counter(format("epdq.authorise-3ds.result.mismatch.account.%s.frontendstatus.%s.gatewaystatus.%s",
                    request.getGatewayAccount().getGatewayName(),
                    request.getAuth3DsDetails().getAuth3DsResult(),
                    authoriseStatus.name()))
                    .inc();
            return reconstructErrorBiasedGatewayResponse(gatewayResponse, authoriseStatus, auth3DResult);

        }
        return gatewayResponse;
    }

    @Override
    public Boolean isNotificationEndpointSecured() {
        return false;
    }

    @Override
    public String getNotificationDomain() {
        return null;
    }

    @Override
    public boolean verifyNotification(Notification<String> notification, GatewayAccountEntity gatewayAccountEntity) {
        if (!notification.getPayload().isPresent()) return false;

        List<NameValuePair> notificationParams = notification.getPayload().get();

        List<NameValuePair> notificationParamsWithoutShaSign = notificationParams.stream()
                .filter(param -> !param.getName().equalsIgnoreCase(SHASIGN_KEY)).collect(toList());

        String signature = signatureGenerator.sign(notificationParamsWithoutShaSign, gatewayAccountEntity.getCredentials().get(CREDENTIALS_SHA_OUT_PASSPHRASE));

        return getShaSignFromNotificationParams(notificationParams).equalsIgnoreCase(signature);
    }

    @Override
    public ExternalChargeRefundAvailability getExternalChargeRefundAvailability(ChargeEntity chargeEntity) {
        return externalRefundAvailabilityCalculator.calculate(chargeEntity);
    }

    @Override
    public Either<String, Notifications<String>> parseNotification(String payload) {
        try {
            Notifications.Builder<String> builder = Notifications.builder();

            EpdqNotification epdqNotification = new EpdqNotification(payload);

            builder.addNotificationFor(
                    epdqNotification.getTransactionId(),
                    epdqNotification.getReference(),
                    epdqNotification.getStatus(),
                    null,
                    epdqNotification.getParams()
            );
            return right(builder.build());
        } catch (Exception e) {
            return left(e.getMessage());
        }
    }

    @Override
    public StatusMapper<String> getStatusMapper() {
        return EpdqStatusMapper.get();
    }

    /**
     * The outgoing response is error biased. Meaning, we have to make sure that the authorisation success can only happen if both frontend as well as epdq confirms its a success.
     * In all other combinations it is not authorised and the frontend error state take precedence followed by gateway error state for the resulting status.
     */
    private GatewayResponse reconstructErrorBiasedGatewayResponse(GatewayResponse<EpdqAuthorisationResponse> gatewayResponse, BaseAuthoriseResponse.AuthoriseStatus authoriseStatus, Auth3dsDetails.Auth3dsResult auth3DResult) {
        GatewayResponse.GatewayResponseBuilder<EpdqAuthorisationResponse> responseBuilder = GatewayResponse.GatewayResponseBuilder.responseBuilder();
        if (auth3DResult.equals(Auth3dsDetails.Auth3dsResult.ERROR)) {
            return responseBuilder.withGatewayError(GatewayError.baseError(format("epdq.authorise-3ds.result.mismatch expected=%s, actual=%s", Auth3dsDetails.Auth3dsResult.ERROR, authoriseStatus.name())))
                    .build();
        } else if (auth3DResult.equals(Auth3dsDetails.Auth3dsResult.DECLINED)) {
            EpdqAuthorisationResponse epdqAuthorisationResponse = new EpdqAuthorisationResponse();
            epdqAuthorisationResponse.setStatusFromAuth3dsResult(auth3DResult);
            return responseBuilder
                    .withResponse(epdqAuthorisationResponse)
                    .build();
        } else {
            return gatewayResponse;
        }
    }

    private boolean responseDoesNotMatchWithUserResult(BaseAuthoriseResponse.AuthoriseStatus authoriseStatus, Auth3dsDetails.Auth3dsResult auth3DResult) {
        boolean respondMatches = (authoriseStatus.equals(ERROR) && auth3DResult.equals(Auth3dsDetails.Auth3dsResult.ERROR)) ||
                (authoriseStatus.equals(AUTHORISED) && auth3DResult.equals(Auth3dsDetails.Auth3dsResult.AUTHORISED)) ||
                (authoriseStatus.equals(REJECTED) && auth3DResult.equals(Auth3dsDetails.Auth3dsResult.DECLINED));
        return !respondMatches;
    }

    private Function<Auth3dsResponseGatewayRequest, GatewayOrder> buildQueryOrderRequestFor() {
        return request -> anEpdqQueryOrderRequestBuilder()
                .withOrderId(request.getChargeExternalId())
                .withPassword(request.getGatewayAccount().getCredentials().get(CREDENTIALS_PASSWORD))
                .withShaInPassphrase(request.getGatewayAccount().getCredentials().get(
                        CREDENTIALS_SHA_IN_PASSPHRASE))
                .withUserId(request.getGatewayAccount().getCredentials().get(CREDENTIALS_USERNAME))
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .build();
    }

    private Function<AuthorisationGatewayRequest, GatewayOrder> buildAuthoriseOrderFor(String frontendUrl) {
        return request -> {
            EpdqOrderRequestBuilder epdqOrderRequestBuilder =
                    request.getGatewayAccount().isRequires3ds() ?
                            anEpdq3DsAuthoriseOrderRequestBuilder(frontendUrl) :
                            anEpdqAuthoriseOrderRequestBuilder();


            return epdqOrderRequestBuilder
                    .withOrderId(request.getChargeExternalId())
                    .withPassword(request.getGatewayAccount().getCredentials().get(CREDENTIALS_PASSWORD))
                    .withShaInPassphrase(request.getGatewayAccount().getCredentials().get(
                            CREDENTIALS_SHA_IN_PASSPHRASE))
                    .withUserId(request.getGatewayAccount().getCredentials().get(CREDENTIALS_USERNAME))
                    .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                    .withDescription(request.getDescription())
                    .withAmount(request.getAmount())
                    .withAuthorisationDetails(request.getAuthCardDetails())
                    .build();
        };
    }

    private Function<CaptureGatewayRequest, GatewayOrder> buildCaptureOrderFor() {
        return request -> anEpdqCaptureOrderRequestBuilder()
                .withUserId(request.getGatewayAccount().getCredentials().get(CREDENTIALS_USERNAME))
                .withPassword(request.getGatewayAccount().getCredentials().get(CREDENTIALS_PASSWORD))
                .withShaInPassphrase(request.getGatewayAccount().getCredentials().get(
                        CREDENTIALS_SHA_IN_PASSPHRASE))
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withTransactionId(request.getTransactionId())
                .build();
    }

    private Function<CancelGatewayRequest, GatewayOrder> buildCancelOrderFor() {
        return request -> anEpdqCancelOrderRequestBuilder()
                .withUserId(request.getGatewayAccount().getCredentials().get(CREDENTIALS_USERNAME))
                .withPassword(request.getGatewayAccount().getCredentials().get(CREDENTIALS_PASSWORD))
                .withShaInPassphrase(request.getGatewayAccount().getCredentials().get(
                        CREDENTIALS_SHA_IN_PASSPHRASE))
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withTransactionId(request.getTransactionId())
                .build();
    }

    private Function<RefundGatewayRequest, GatewayOrder> buildRefundOrderFor() {
        return request -> anEpdqRefundOrderRequestBuilder()
                .withUserId(request.getGatewayAccount().getCredentials().get(CREDENTIALS_USERNAME))
                .withPassword(request.getGatewayAccount().getCredentials().get(CREDENTIALS_PASSWORD))
                .withShaInPassphrase(request.getGatewayAccount().getCredentials().get(
                        CREDENTIALS_SHA_IN_PASSPHRASE))
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withTransactionId(request.getTransactionId())
                .withAmount(request.getAmount())
                .build();
    }

    private Function<GatewayClient.Response, Optional<String>> extractResponseIdentifier() {
        return response -> {
            Optional<String> emptyResponseIdentifierForEpdq = Optional.empty();
            return emptyResponseIdentifierForEpdq;
        };
    }

    public static BiFunction<GatewayOrder, Invocation.Builder, Invocation.Builder> includeSessionIdentifier() {
        return (order, builder) -> builder;
    }

    private String getShaSignFromNotificationParams(List<NameValuePair> notificationParams) {
        return notificationParams.stream()
                .filter(param -> param.getName().equalsIgnoreCase(SHASIGN_KEY))
                .findFirst()
                .map(NameValuePair::getValue)
                .orElse("");
    }
}
