package uk.gov.pay.connector.gateway.epdq;

import com.codahale.metrics.MetricRegistry;
import fj.data.Either;
import io.dropwizard.setup.Environment;
import org.apache.http.NameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.gateway.CaptureHandler;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayClientFactory;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.StatusMapper;
import uk.gov.pay.connector.gateway.epdq.model.response.EpdqAuthorisationResponse;
import uk.gov.pay.connector.gateway.epdq.model.response.EpdqCancelResponse;
import uk.gov.pay.connector.gateway.epdq.model.response.EpdqRefundResponse;
import uk.gov.pay.connector.gateway.model.Auth3dsDetails;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.AuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.BaseAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.BaseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.util.EpdqExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.gateway.util.ExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.gateway.util.GatewayResponseGenerator;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.usernotification.model.Notification;
import uk.gov.pay.connector.usernotification.model.Notifications;

import javax.inject.Inject;
import javax.ws.rs.client.Invocation;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

import static fj.data.Either.left;
import static fj.data.Either.right;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static uk.gov.pay.connector.gateway.GatewayOperation.AUTHORISE;
import static uk.gov.pay.connector.gateway.GatewayOperation.CANCEL;
import static uk.gov.pay.connector.gateway.GatewayOperation.CAPTURE;
import static uk.gov.pay.connector.gateway.GatewayOperation.REFUND;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.EPDQ;
import static uk.gov.pay.connector.gateway.epdq.EpdqNotification.SHASIGN_KEY;
import static uk.gov.pay.connector.gateway.epdq.EpdqOrderRequestBuilder.anEpdq3DsAuthoriseOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.epdq.EpdqOrderRequestBuilder.anEpdqAuthoriseOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.epdq.EpdqOrderRequestBuilder.anEpdqCancelOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.epdq.EpdqOrderRequestBuilder.anEpdqQueryOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.epdq.EpdqOrderRequestBuilder.anEpdqRefundOrderRequestBuilder;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_SHA_IN_PASSPHRASE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_SHA_OUT_PASSPHRASE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;

public class EpdqPaymentProvider implements PaymentProvider<BaseResponse, String> {

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
    private final GatewayClient authoriseClient;
    private final GatewayClient cancelClient;
    private final GatewayClient captureClient;
    private final GatewayClient refundClient;
    private final ExternalRefundAvailabilityCalculator externalRefundAvailabilityCalculator;

    private final EpdqCaptureHandler epdqCaptureHandler;

    @Inject
    public EpdqPaymentProvider(ConnectorConfiguration configuration,
                               GatewayClientFactory gatewayClientFactory,
                               Environment environment,
                               SignatureGenerator signatureGenerator) {
        authoriseClient = gatewayClientFactory.createGatewayClient(EPDQ, AUTHORISE, configuration.getGatewayConfigFor(EPDQ).getUrls(), includeSessionIdentifier(), environment.metrics());
        cancelClient = gatewayClientFactory.createGatewayClient(EPDQ, CANCEL, configuration.getGatewayConfigFor(EPDQ).getUrls(), includeSessionIdentifier(), environment.metrics());
        captureClient = gatewayClientFactory.createGatewayClient(EPDQ, CAPTURE, configuration.getGatewayConfigFor(EPDQ).getUrls(), includeSessionIdentifier(), environment.metrics());
        refundClient = gatewayClientFactory.createGatewayClient(EPDQ, REFUND, configuration.getGatewayConfigFor(EPDQ).getUrls(), includeSessionIdentifier(), environment.metrics());
        this.signatureGenerator = signatureGenerator;
        this.frontendUrl = configuration.getLinks().getFrontendUrl();
        this.metricRegistry = environment.metrics();
        this.externalRefundAvailabilityCalculator = new EpdqExternalRefundAvailabilityCalculator();

        epdqCaptureHandler = new EpdqCaptureHandler(captureClient);
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
    public GatewayResponse authorise(BaseAuthorisationGatewayRequest request) {
        Either<GatewayError, GatewayClient.Response> response = authoriseClient.postRequestFor(ROUTE_FOR_NEW_ORDER, request.getGatewayAccount(), buildAuthoriseOrder(request, frontendUrl));
        return GatewayResponseGenerator.getEpdqGatewayResponse(authoriseClient, response, EpdqAuthorisationResponse.class);
    }

    @Override
    public GatewayResponse refund(RefundGatewayRequest request) {
        Either<GatewayError, GatewayClient.Response> response = refundClient.postRequestFor(ROUTE_FOR_MAINTENANCE_ORDER, request.getGatewayAccount(), buildRefundOrder(request));
        return GatewayResponseGenerator.getEpdqGatewayResponse(refundClient, response, EpdqRefundResponse.class);
    }

    @Override
    public GatewayResponse cancel(CancelGatewayRequest request) {
        Either<GatewayError, GatewayClient.Response> response = cancelClient.postRequestFor(ROUTE_FOR_MAINTENANCE_ORDER, request.getGatewayAccount(), buildCancelOrder(request));
        return GatewayResponseGenerator.getEpdqGatewayResponse(cancelClient, response, EpdqCancelResponse.class);
    }

    @Override
    public GatewayResponse authorise3dsResponse(Auth3dsResponseGatewayRequest request) {
        Either<GatewayError, GatewayClient.Response> response = authoriseClient.postRequestFor(ROUTE_FOR_QUERY_ORDER, request.getGatewayAccount(), buildQueryOrderRequestFor(request));
        GatewayResponse<BaseResponse> gatewayResponse = GatewayResponseGenerator.getEpdqGatewayResponse(authoriseClient, response, EpdqAuthorisationResponse.class);

        BaseAuthoriseResponse.AuthoriseStatus authoriseStatus = gatewayResponse.getBaseResponse().map(epdqStatus -> ((EpdqAuthorisationResponse) epdqStatus).authoriseStatus()).orElse(BaseAuthoriseResponse.AuthoriseStatus.ERROR);
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
    public CaptureHandler getCaptureHandler() {
        return epdqCaptureHandler;
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
    private GatewayResponse reconstructErrorBiasedGatewayResponse(GatewayResponse<BaseResponse> gatewayResponse, BaseAuthoriseResponse.AuthoriseStatus authoriseStatus, Auth3dsDetails.Auth3dsResult auth3DResult) {
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
        boolean respondMatches = (authoriseStatus.equals(BaseAuthoriseResponse.AuthoriseStatus.ERROR) && auth3DResult.equals(Auth3dsDetails.Auth3dsResult.ERROR)) ||
                (authoriseStatus.equals(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED) && auth3DResult.equals(Auth3dsDetails.Auth3dsResult.AUTHORISED)) ||
                (authoriseStatus.equals(BaseAuthoriseResponse.AuthoriseStatus.REJECTED) && auth3DResult.equals(Auth3dsDetails.Auth3dsResult.DECLINED));
        return !respondMatches;
    }

    private GatewayOrder buildQueryOrderRequestFor(Auth3dsResponseGatewayRequest request) {
        return anEpdqQueryOrderRequestBuilder()
                .withOrderId(request.getChargeExternalId())
                .withPassword(request.getGatewayAccount().getCredentials().get(CREDENTIALS_PASSWORD))
                .withShaInPassphrase(request.getGatewayAccount().getCredentials().get(
                        CREDENTIALS_SHA_IN_PASSPHRASE))
                .withUserId(request.getGatewayAccount().getCredentials().get(CREDENTIALS_USERNAME))
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .build();
    }

    private GatewayOrder buildAuthoriseOrder(AuthorisationGatewayRequest request, String frontendUrl) {
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
    }

    private GatewayOrder buildCancelOrder(CancelGatewayRequest request) {
        return anEpdqCancelOrderRequestBuilder()
                .withUserId(request.getGatewayAccount().getCredentials().get(CREDENTIALS_USERNAME))
                .withPassword(request.getGatewayAccount().getCredentials().get(CREDENTIALS_PASSWORD))
                .withShaInPassphrase(request.getGatewayAccount().getCredentials().get(
                        CREDENTIALS_SHA_IN_PASSPHRASE))
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withTransactionId(request.getTransactionId())
                .build();
    }

    private GatewayOrder buildRefundOrder(RefundGatewayRequest request) {
        return anEpdqRefundOrderRequestBuilder()
                .withUserId(request.getGatewayAccount().getCredentials().get(CREDENTIALS_USERNAME))
                .withPassword(request.getGatewayAccount().getCredentials().get(CREDENTIALS_PASSWORD))
                .withShaInPassphrase(request.getGatewayAccount().getCredentials().get(
                        CREDENTIALS_SHA_IN_PASSPHRASE))
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withTransactionId(request.getTransactionId())
                .withAmount(request.getAmount())
                .build();
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
