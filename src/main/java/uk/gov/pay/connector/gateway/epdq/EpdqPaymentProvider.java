package uk.gov.pay.connector.gateway.epdq;

import com.codahale.metrics.MetricRegistry;
import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayClientFactory;
import uk.gov.pay.connector.gateway.GatewayErrorException;
import uk.gov.pay.connector.gateway.GatewayErrorException.GatewayConnectionErrorException;
import uk.gov.pay.connector.gateway.GatewayErrorException.GenericGatewayErrorException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.epdq.model.response.EpdqAuthorisationResponse;
import uk.gov.pay.connector.gateway.epdq.model.response.EpdqCancelResponse;
import uk.gov.pay.connector.gateway.epdq.model.response.EpdqQueryResponse;
import uk.gov.pay.connector.gateway.model.Auth3dsDetails.Auth3dsResult;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.BaseResponse;
import uk.gov.pay.connector.gateway.model.response.Gateway3DSAuthorisationResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.util.EpdqExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.gateway.util.ExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.wallets.WalletAuthorisationGatewayRequest;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Invocation;
import java.nio.charset.Charset;
import java.util.Optional;
import java.util.function.BiFunction;

import static java.lang.String.format;
import static uk.gov.pay.connector.gateway.GatewayOperation.AUTHORISE;
import static uk.gov.pay.connector.gateway.GatewayOperation.CANCEL;
import static uk.gov.pay.connector.gateway.GatewayOperation.CAPTURE;
import static uk.gov.pay.connector.gateway.GatewayOperation.REFUND;
import static uk.gov.pay.connector.gateway.GatewayResponseUnmarshaller.unmarshallResponse;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.EPDQ;
import static uk.gov.pay.connector.gateway.epdq.EpdqOrderRequestBuilder.anEpdq3DsAuthoriseOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.epdq.EpdqOrderRequestBuilder.anEpdqAuthoriseOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.epdq.EpdqOrderRequestBuilder.anEpdqCancelOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.epdq.EpdqOrderRequestBuilder.anEpdqQueryOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse.AuthoriseStatus.ERROR;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_SHA_IN_PASSPHRASE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;

public class EpdqPaymentProvider implements PaymentProvider {

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

    private final String frontendUrl;
    private final MetricRegistry metricRegistry;
    private final GatewayClient authoriseClient;
    private final GatewayClient cancelClient;
    private final GatewayClient captureClient;
    private final GatewayClient refundClient;
    private final ExternalRefundAvailabilityCalculator externalRefundAvailabilityCalculator;

    private final EpdqCaptureHandler epdqCaptureHandler;
    private final EpdqRefundHandler epdqRefundHandler;

    @Inject
    public EpdqPaymentProvider(ConnectorConfiguration configuration,
                               GatewayClientFactory gatewayClientFactory,
                               Environment environment) {
        authoriseClient = gatewayClientFactory.createGatewayClient(EPDQ, AUTHORISE, configuration.getGatewayConfigFor(EPDQ).getUrls(), includeSessionIdentifier(), environment.metrics());
        cancelClient = gatewayClientFactory.createGatewayClient(EPDQ, CANCEL, configuration.getGatewayConfigFor(EPDQ).getUrls(), includeSessionIdentifier(), environment.metrics());
        captureClient = gatewayClientFactory.createGatewayClient(EPDQ, CAPTURE, configuration.getGatewayConfigFor(EPDQ).getUrls(), includeSessionIdentifier(), environment.metrics());
        refundClient = gatewayClientFactory.createGatewayClient(EPDQ, REFUND, configuration.getGatewayConfigFor(EPDQ).getUrls(), includeSessionIdentifier(), environment.metrics());
        this.frontendUrl = configuration.getLinks().getFrontendUrl();
        this.metricRegistry = environment.metrics();
        this.externalRefundAvailabilityCalculator = new EpdqExternalRefundAvailabilityCalculator();

        epdqCaptureHandler = new EpdqCaptureHandler(captureClient);
        epdqRefundHandler = new EpdqRefundHandler(refundClient);
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
    public GatewayResponse<BaseAuthoriseResponse> authorise(CardAuthorisationGatewayRequest request) throws GatewayErrorException {
        GatewayClient.Response response = authoriseClient.postRequestFor(ROUTE_FOR_NEW_ORDER, request.getGatewayAccount(), buildAuthoriseOrder(request, frontendUrl));
        return getEpdqGatewayResponse(response, EpdqAuthorisationResponse.class);
    }

    private static GatewayResponse getEpdqGatewayResponse(GatewayClient.Response response, Class<? extends BaseResponse> responseClass) throws GatewayConnectionErrorException {
        GatewayResponse.GatewayResponseBuilder<BaseResponse> responseBuilder = GatewayResponse.GatewayResponseBuilder.responseBuilder();
        responseBuilder.withResponse(unmarshallResponse(response, responseClass));
        return responseBuilder.build();
    }
    
    private static GatewayResponse getUninterpretedEpdqGatewayResponse(GatewayClient.Response response, Class<? extends BaseResponse> responseClass) throws GatewayConnectionErrorException {
        GatewayResponse.GatewayResponseBuilder<BaseResponse> responseBuilder = GatewayResponse.GatewayResponseBuilder.responseBuilder();
        responseBuilder.withResponse(unmarshallResponse(response, responseClass));
        return responseBuilder.buildUninterpreted();
    }

    @Override
    public GatewayRefundResponse refund(RefundGatewayRequest request) {
        return epdqRefundHandler.refund(request);
    }

    @Override
    public GatewayResponse<BaseCancelResponse> cancel(CancelGatewayRequest request) throws GatewayErrorException {
        GatewayClient.Response response = cancelClient.postRequestFor(ROUTE_FOR_MAINTENANCE_ORDER, request.getGatewayAccount(), buildCancelOrder(request));
        return getEpdqGatewayResponse(response, EpdqCancelResponse.class);
    }

    @Override
    public GatewayResponse<BaseAuthoriseResponse> authoriseWallet(WalletAuthorisationGatewayRequest request) {
        throw new UnsupportedOperationException("Wallets are not supported for ePDQ");
    }

    public ChargeQueryResponse queryPaymentStatus(ChargeEntity charge) throws GatewayErrorException {
        GatewayClient.Response response = authoriseClient.postRequestFor(ROUTE_FOR_QUERY_ORDER, charge.getGatewayAccount(), buildQueryOrderRequestFor(charge));
        GatewayResponse<EpdqQueryResponse> epdqGatewayResponse = getUninterpretedEpdqGatewayResponse(response, EpdqQueryResponse.class);

        return epdqGatewayResponse.getBaseResponse()
                .map(EpdqQueryResponse::toChargeQueryResponse)
                .orElseThrow(() -> 
                        new WebApplicationException(String.format(
                                "Unable to query charge %s - an error occurred: %s",
                                charge.getExternalId(),
                                epdqGatewayResponse
                        )));
            
    }

    @Override
    public Gateway3DSAuthorisationResponse authorise3dsResponse(Auth3dsResponseGatewayRequest request) {
        Optional<String> transactionId = Optional.empty();
        String stringifiedResponse = null;
        BaseAuthoriseResponse.AuthoriseStatus authorisationStatus = null;
        
        try {
            GatewayClient.Response response = authoriseClient.postRequestFor(ROUTE_FOR_QUERY_ORDER, request.getGatewayAccount(), buildQueryOrderRequestFor(request));
            GatewayResponse<BaseAuthoriseResponse> gatewayResponse = getEpdqGatewayResponse(response, EpdqAuthorisationResponse.class);
            BaseAuthoriseResponse.AuthoriseStatus authoriseStatus = gatewayResponse.getBaseResponse()
                    .map(BaseAuthoriseResponse::authoriseStatus).orElse(ERROR);
            Auth3dsResult auth3DResult = request.getAuth3DsDetails().getAuth3DsResult() == null ?
                    Auth3dsResult.ERROR : // we treat no result from frontend as an error
                    request.getAuth3DsDetails().getAuth3DsResult();
            
            if (responseDoesNotMatchWithUserResult(authoriseStatus, auth3DResult)) {
                LOGGER.warn("epdq.authorise-3ds.result.mismatch for chargeId={}, frontendstatus={}, gatewaystatus={}",
                        request.getChargeExternalId(), request.getGatewayAccount().getId(), auth3DResult, authoriseStatus);
                metricRegistry.counter(format("epdq.authorise-3ds.result.mismatch.account.%s.frontendstatus.%s.gatewaystatus.%s",
                        request.getGatewayAccount().getGatewayName(),
                        request.getAuth3DsDetails().getAuth3DsResult(),
                        authoriseStatus.name()))
                        .inc();
                gatewayResponse = reconstructErrorBiasedGatewayResponse(gatewayResponse, authoriseStatus, auth3DResult);
            }
            
            if (!gatewayResponse.getBaseResponse().isPresent()) gatewayResponse.throwGatewayError();
            
            transactionId = Optional.ofNullable(gatewayResponse.getBaseResponse().get().getTransactionId());
            stringifiedResponse = gatewayResponse.toString();
            authorisationStatus = gatewayResponse.getBaseResponse().get().authoriseStatus();
            
        } catch (GatewayErrorException e) {
            stringifiedResponse = e.getMessage();
            authorisationStatus = BaseAuthoriseResponse.AuthoriseStatus.EXCEPTION;
        }
        
        if (transactionId.isPresent()) {
            return Gateway3DSAuthorisationResponse.of(stringifiedResponse, authorisationStatus, transactionId.get());
        } else {
            return Gateway3DSAuthorisationResponse.of(stringifiedResponse, authorisationStatus);
        }
    }

    @Override
    public CaptureResponse capture(CaptureGatewayRequest request) {
        return epdqCaptureHandler.capture(request);
    }

    @Override
    public ExternalChargeRefundAvailability getExternalChargeRefundAvailability(ChargeEntity chargeEntity) {
        return externalRefundAvailabilityCalculator.calculate(chargeEntity);
    }

    /**
     * The outgoing response is error biased. Meaning, we have to make sure that the authorisation success can only happen if both frontend as well as epdq confirms its a success.
     * In all other combinations it is not authorised and the frontend error state take precedence followed by gateway error state for the resulting status.
     */
    private GatewayResponse<BaseAuthoriseResponse> reconstructErrorBiasedGatewayResponse(
            GatewayResponse<BaseAuthoriseResponse> gatewayResponse,
            BaseAuthoriseResponse.AuthoriseStatus authoriseStatus,
            Auth3dsResult auth3DResult) throws GenericGatewayErrorException {

        GatewayResponse.GatewayResponseBuilder<EpdqAuthorisationResponse> responseBuilder = GatewayResponse.GatewayResponseBuilder.responseBuilder();
        if (auth3DResult.equals(Auth3dsResult.ERROR)) {
            throw new GenericGatewayErrorException(
                    format("epdq.authorise-3ds.result.mismatch expected=%s, actual=%s", Auth3dsResult.ERROR, authoriseStatus.name()));
        } else if (auth3DResult.equals(Auth3dsResult.DECLINED)) {
            EpdqAuthorisationResponse epdqAuthorisationResponse = new EpdqAuthorisationResponse();
            epdqAuthorisationResponse.setStatusFromAuth3dsResult(auth3DResult);
            return responseBuilder
                    .withResponse(epdqAuthorisationResponse)
                    .build();
        } else {
            return gatewayResponse;
        }
    }

    private static boolean responseDoesNotMatchWithUserResult(BaseAuthoriseResponse.AuthoriseStatus authoriseStatus, Auth3dsResult auth3DResult) {
        boolean respondMatches = (authoriseStatus.equals(ERROR) && auth3DResult.equals(Auth3dsResult.ERROR)) ||
                (authoriseStatus.equals(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED) && auth3DResult.equals(Auth3dsResult.AUTHORISED)) ||
                (authoriseStatus.equals(BaseAuthoriseResponse.AuthoriseStatus.REJECTED) && auth3DResult.equals(Auth3dsResult.DECLINED));
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

    private GatewayOrder buildQueryOrderRequestFor(ChargeEntity charge) {
        return anEpdqQueryOrderRequestBuilder()
                .withOrderId(charge.getExternalId())
                .withPassword(charge.getGatewayAccount().getCredentials().get(CREDENTIALS_PASSWORD))
                .withShaInPassphrase(charge.getGatewayAccount().getCredentials().get(
                        CREDENTIALS_SHA_IN_PASSPHRASE))
                .withUserId(charge.getGatewayAccount().getCredentials().get(CREDENTIALS_USERNAME))
                .withMerchantCode(charge.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .build();
    }

    private GatewayOrder buildAuthoriseOrder(CardAuthorisationGatewayRequest request, String frontendUrl) {
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

    public static BiFunction<GatewayOrder, Invocation.Builder, Invocation.Builder> includeSessionIdentifier() {
        return (order, builder) -> builder;
    }
}
