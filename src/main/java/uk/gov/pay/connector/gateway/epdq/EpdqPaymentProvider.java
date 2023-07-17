package uk.gov.pay.connector.gateway.epdq;

import com.codahale.metrics.MetricRegistry;
import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.ChargeQueryGatewayRequest;
import uk.gov.pay.connector.gateway.ChargeQueryResponse;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayClientFactory;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.GatewayException.GatewayErrorException;
import uk.gov.pay.connector.gateway.GatewayException.GenericGatewayException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.epdq.model.response.EpdqAuthorisationResponse;
import uk.gov.pay.connector.gateway.epdq.model.response.EpdqCancelResponse;
import uk.gov.pay.connector.gateway.epdq.model.response.EpdqQueryResponse;
import uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForCancelOrder;
import uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNew3ds2Order;
import uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNew3dsOrder;
import uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNewOrder;
import uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForQueryOrder;
import uk.gov.pay.connector.gateway.model.Auth3dsResult.Auth3dsResultOutcome;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
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
import uk.gov.pay.connector.gatewayaccount.model.EpdqCredentials;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.refund.model.domain.Refund;
import uk.gov.pay.connector.wallets.WalletAuthorisationGatewayRequest;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import java.net.URI;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.lang.String.format;
import static uk.gov.pay.connector.gateway.GatewayOperation.AUTHORISE;
import static uk.gov.pay.connector.gateway.GatewayOperation.CANCEL;
import static uk.gov.pay.connector.gateway.GatewayOperation.CAPTURE;
import static uk.gov.pay.connector.gateway.GatewayOperation.REFUND;
import static uk.gov.pay.connector.gateway.GatewayResponseUnmarshaller.unmarshallResponse;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.EPDQ;
import static uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse.AuthoriseStatus.ERROR;
import static uk.gov.pay.connector.gateway.util.AuthUtil.getEpdqAuthHeader;

public class EpdqPaymentProvider implements PaymentProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(EpdqPaymentProvider.class);

    public static final String ROUTE_FOR_NEW_ORDER = "orderdirect.asp";
    public static final String ROUTE_FOR_MAINTENANCE_ORDER = "maintenancedirect.asp";
    public static final String ROUTE_FOR_QUERY_ORDER = "querydirect.asp";

    private final String frontendUrl;
    private final MetricRegistry metricRegistry;
    private final GatewayClient authoriseClient;
    private final GatewayClient cancelClient;
    private final ExternalRefundAvailabilityCalculator externalRefundAvailabilityCalculator;
    private final Map<String, String> gatewayUrlMap;
    private final EpdqCaptureHandler epdqCaptureHandler;
    private final EpdqRefundHandler epdqRefundHandler;
    private final Clock clock;

    @Inject
    public EpdqPaymentProvider(ConnectorConfiguration configuration, GatewayClientFactory gatewayClientFactory, Environment environment, Clock clock) {
        gatewayUrlMap = configuration.getGatewayConfigFor(EPDQ).getUrls();
        authoriseClient = gatewayClientFactory.createGatewayClient(EPDQ, AUTHORISE, environment.metrics());
        cancelClient = gatewayClientFactory.createGatewayClient(EPDQ, CANCEL, environment.metrics());
        GatewayClient captureClient = gatewayClientFactory.createGatewayClient(EPDQ, CAPTURE, environment.metrics());
        GatewayClient refundClient = gatewayClientFactory.createGatewayClient(EPDQ, REFUND, environment.metrics());
        frontendUrl = configuration.getLinks().getFrontendUrl();
        metricRegistry = environment.metrics();
        externalRefundAvailabilityCalculator = new EpdqExternalRefundAvailabilityCalculator();
        epdqCaptureHandler = new EpdqCaptureHandler(captureClient, gatewayUrlMap);
        epdqRefundHandler = new EpdqRefundHandler(refundClient, gatewayUrlMap);
        this.clock = clock;
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
    public GatewayResponse<BaseAuthoriseResponse> authorise(CardAuthorisationGatewayRequest request, ChargeEntity charge) throws GatewayException {
        URI url = URI.create(String.format("%s/%s", gatewayUrlMap.get(request.getGatewayAccount().getType()), ROUTE_FOR_NEW_ORDER));
        GatewayClient.Response response = authoriseClient.postRequestFor(
                url,
                EPDQ,
                request.getGatewayAccount().getType(),
                buildAuthoriseOrder(request, frontendUrl), 
                getEpdqAuthHeader(request.getGatewayCredentials()));
        return getEpdqGatewayResponse(response, EpdqAuthorisationResponse.class);
    }

    private static GatewayResponse getEpdqGatewayResponse(GatewayClient.Response response, Class<? extends BaseResponse> responseClass) throws GatewayErrorException {
        var responseBuilder = GatewayResponse.GatewayResponseBuilder.responseBuilder();
        responseBuilder.withResponse(unmarshallResponse(response, responseClass));
        return responseBuilder.build();
    }

    private static GatewayResponse getUninterpretedEpdqGatewayResponse(GatewayClient.Response response, Class<? extends BaseResponse> responseClass) throws GatewayErrorException {
        var responseBuilder = GatewayResponse.GatewayResponseBuilder.responseBuilder();
        responseBuilder.withResponse(unmarshallResponse(response, responseClass));
        return responseBuilder.buildUninterpreted();
    }

    @Override
    public GatewayResponse authoriseMotoApi(CardAuthorisationGatewayRequest request) {
        throw new UnsupportedOperationException("MOTO API payments are not supported for ePDQ");
    }

    @Override
    public GatewayRefundResponse refund(RefundGatewayRequest request) {
        return epdqRefundHandler.refund(request);
    }

    @Override
    public GatewayResponse<BaseCancelResponse> cancel(CancelGatewayRequest request) throws GatewayException {
        URI url = URI.create(String.format("%s/%s", gatewayUrlMap.get(request.getGatewayAccount().getType()), ROUTE_FOR_MAINTENANCE_ORDER));
        GatewayClient.Response response = cancelClient.postRequestFor(
                url,
                EPDQ,
                request.getGatewayAccount().getType(),
                buildCancelOrder(request),
                getEpdqAuthHeader(request.getGatewayCredentials()));
        return getEpdqGatewayResponse(response, EpdqCancelResponse.class);
    }

    @Override
    public GatewayResponse<BaseAuthoriseResponse> authoriseWallet(WalletAuthorisationGatewayRequest request) {
        throw new UnsupportedOperationException("Wallets are not supported for ePDQ");
    }

    public ChargeQueryResponse queryPaymentStatus(ChargeQueryGatewayRequest chargeQueryGatewayRequest) throws GatewayException {
        URI url = URI.create(String.format("%s/%s", gatewayUrlMap.get(chargeQueryGatewayRequest.getGatewayAccount().getType()), ROUTE_FOR_QUERY_ORDER));
        GatewayClient.Response response = authoriseClient.postRequestFor(
                url,
                EPDQ,
                chargeQueryGatewayRequest.getGatewayAccount().getType(),
                buildQueryOrderRequestFor(chargeQueryGatewayRequest),
                getEpdqAuthHeader(chargeQueryGatewayRequest.getGatewayCredentials()));
        GatewayResponse<EpdqQueryResponse> epdqGatewayResponse = getUninterpretedEpdqGatewayResponse(response, EpdqQueryResponse.class);

        return epdqGatewayResponse.getBaseResponse()
                .map(epdqQueryResponse -> {
                    ChargeStatus mappedStatus = EpdqStatusMapper.map(epdqQueryResponse.getStatus());
                    return new ChargeQueryResponse(mappedStatus, epdqQueryResponse);
                })
                .orElseThrow(() ->
                        new WebApplicationException(String.format(
                                "Unable to query charge %s - an error occurred: %s",
                                chargeQueryGatewayRequest.getChargeExternalId(),
                                epdqGatewayResponse
                        )));
            
    }

    @Override
    public boolean canQueryPaymentStatus() {
        return true;
    }

    @Override
    public Gateway3DSAuthorisationResponse authorise3dsResponse(Auth3dsResponseGatewayRequest request) {
        Optional<String> transactionId = Optional.empty();
        String stringifiedResponse;
        BaseAuthoriseResponse.AuthoriseStatus authorisationStatus;
        
        try {
            URI url = URI.create(String.format("%s/%s", gatewayUrlMap.get(request.getGatewayAccount().getType()), ROUTE_FOR_QUERY_ORDER));
            GatewayClient.Response response = authoriseClient.postRequestFor(
                    url,
                    EPDQ,
                    request.getGatewayAccount().getType(),
                    buildQueryOrderRequestFor(request),
                    getEpdqAuthHeader(request.getGatewayCredentials()));
            GatewayResponse<BaseAuthoriseResponse> gatewayResponse = getEpdqGatewayResponse(response, EpdqAuthorisationResponse.class);
            BaseAuthoriseResponse.AuthoriseStatus authoriseStatus = gatewayResponse.getBaseResponse()
                    .map(BaseAuthoriseResponse::authoriseStatus).orElse(ERROR);
            Auth3dsResultOutcome auth3DResult = request.getAuth3dsResult().getAuth3dsResultOutcome() == null ?
                    Auth3dsResultOutcome.ERROR : // we treat no result from frontend as an error
                    request.getAuth3dsResult().getAuth3dsResultOutcome();
            
            if (responseDoesNotMatchWithUserResult(authoriseStatus, auth3DResult)) {
                LOGGER.warn("epdq.authorise-3ds.result.mismatch for chargeId={}, gatewayAccountId={}, frontendstatus={}, gatewaystatus={}",
                        request.getChargeExternalId(), request.getGatewayAccount().getId(), auth3DResult, authoriseStatus);
                metricRegistry.counter(format("epdq.authorise-3ds.result.mismatch.account.%s.frontendstatus.%s.gatewaystatus.%s",
                        EPDQ.getName(),
                        request.getAuth3dsResult().getAuth3dsResultOutcome(),
                        authoriseStatus.name()))
                        .inc();
                gatewayResponse = reconstructErrorBiasedGatewayResponse(gatewayResponse, authoriseStatus, auth3DResult);
            }
            
            if (gatewayResponse.getBaseResponse().isEmpty()) gatewayResponse.throwGatewayError();
            
            transactionId = Optional.ofNullable(gatewayResponse.getBaseResponse().get().getTransactionId());
            stringifiedResponse = gatewayResponse.toString();
            authorisationStatus = gatewayResponse.getBaseResponse().get().authoriseStatus();
            
        } catch (GatewayException e) {
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
    public ExternalChargeRefundAvailability getExternalChargeRefundAvailability(Charge charge, List<Refund> refundList) {
        return externalRefundAvailabilityCalculator.calculate(charge, refundList);
    }

    @Override
    public EpdqAuthorisationRequestSummary generateAuthorisationRequestSummary(GatewayAccountEntity gatewayAccount, AuthCardDetails authCardDetails, boolean isSetUpAgreement) {
        return new EpdqAuthorisationRequestSummary(gatewayAccount, authCardDetails);
    }

    /**
     * The outgoing response is error biased. Meaning, we have to make sure that the authorisation success can only happen if both frontend as well as epdq confirms its a success.
     * In all other combinations it is not authorised and the frontend error state take precedence followed by gateway error state for the resulting status.
     */
    private GatewayResponse<BaseAuthoriseResponse> reconstructErrorBiasedGatewayResponse(
            GatewayResponse<BaseAuthoriseResponse> gatewayResponse,
            BaseAuthoriseResponse.AuthoriseStatus authoriseStatus,
            Auth3dsResultOutcome auth3dsResultOutcome) throws GenericGatewayException {

        var responseBuilder = GatewayResponse.GatewayResponseBuilder.responseBuilder();
        if (auth3dsResultOutcome.equals(Auth3dsResultOutcome.ERROR)) {
            throw new GenericGatewayException(
                    format("epdq.authorise-3ds.result.mismatch expected=%s, actual=%s", Auth3dsResultOutcome.ERROR, authoriseStatus.name()));
        } else if (auth3dsResultOutcome.equals(Auth3dsResultOutcome.DECLINED)) {
            EpdqAuthorisationResponse epdqAuthorisationResponse = new EpdqAuthorisationResponse();
            epdqAuthorisationResponse.setStatusFromAuth3dsResult(auth3dsResultOutcome);
            return responseBuilder
                    .withResponse(epdqAuthorisationResponse)
                    .build();
        } else {
            return gatewayResponse;
        }
    }

    private static boolean responseDoesNotMatchWithUserResult(BaseAuthoriseResponse.AuthoriseStatus authoriseStatus, Auth3dsResultOutcome auth3DResult) {
        boolean respondMatches = (authoriseStatus.equals(ERROR) && auth3DResult.equals(Auth3dsResultOutcome.ERROR)) ||
                (authoriseStatus.equals(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED) && auth3DResult.equals(Auth3dsResultOutcome.AUTHORISED)) ||
                (authoriseStatus.equals(BaseAuthoriseResponse.AuthoriseStatus.REJECTED) && auth3DResult.equals(Auth3dsResultOutcome.DECLINED));
        return !respondMatches;
    }

    private GatewayOrder buildQueryOrderRequestFor(Auth3dsResponseGatewayRequest request) {
        var epdqCredentials = (EpdqCredentials)request.getGatewayCredentials();
        var epdqPayloadDefinitionForQueryOrder = new EpdqPayloadDefinitionForQueryOrder();
        epdqPayloadDefinitionForQueryOrder.setOrderId(request.getChargeExternalId());
        epdqPayloadDefinitionForQueryOrder.setPassword(epdqCredentials.getPassword());
        epdqPayloadDefinitionForQueryOrder.setUserId(epdqCredentials.getUsername());
        epdqPayloadDefinitionForQueryOrder.setPspId(epdqCredentials.getMerchantId());
        epdqPayloadDefinitionForQueryOrder.setShaInPassphrase(epdqCredentials.getShaInPassphrase());
        return epdqPayloadDefinitionForQueryOrder.createGatewayOrder();
    }

    private GatewayOrder buildQueryOrderRequestFor(ChargeQueryGatewayRequest chargeQueryGatewayRequest) {
        var epdqCredentials = (EpdqCredentials)chargeQueryGatewayRequest.getGatewayCredentials();
        var epdqPayloadDefinitionForQueryOrder = new EpdqPayloadDefinitionForQueryOrder();
        epdqPayloadDefinitionForQueryOrder.setOrderId(chargeQueryGatewayRequest.getChargeExternalId());
        epdqPayloadDefinitionForQueryOrder.setPassword(epdqCredentials.getPassword());
        epdqPayloadDefinitionForQueryOrder.setUserId(epdqCredentials.getUsername());
        epdqPayloadDefinitionForQueryOrder.setPspId(epdqCredentials.getMerchantId());
        epdqPayloadDefinitionForQueryOrder.setShaInPassphrase(epdqCredentials.getShaInPassphrase());
        return epdqPayloadDefinitionForQueryOrder.createGatewayOrder();
    }

    private GatewayOrder buildAuthoriseOrder(CardAuthorisationGatewayRequest request, String frontendUrl) {
        EpdqPayloadDefinitionForNewOrder epdqPayloadDefinition;

        if (request.getGatewayAccount().isRequires3ds()) {
            if (request.getGatewayAccount().getIntegrationVersion3ds() == 2) {
                epdqPayloadDefinition = new EpdqPayloadDefinitionForNew3ds2Order(frontendUrl, request.getGatewayAccount().isSendPayerIpAddressToGateway(), request.getLanguage(), clock);
            } else {
                epdqPayloadDefinition = new EpdqPayloadDefinitionForNew3dsOrder(frontendUrl);
            }
        } else {
            epdqPayloadDefinition = new EpdqPayloadDefinitionForNewOrder();
        }

        var credentials = (EpdqCredentials)request.getGatewayCredentials();
        epdqPayloadDefinition.setOrderId(request.getGovUkPayPaymentId());
        epdqPayloadDefinition.setPassword(credentials.getPassword());
        epdqPayloadDefinition.setUserId(credentials.getUsername());
        epdqPayloadDefinition.setPspId(credentials.getMerchantId());
        epdqPayloadDefinition.setAmount(request.getAmount());
        epdqPayloadDefinition.setAuthCardDetails(request.getAuthCardDetails());
        epdqPayloadDefinition.setShaInPassphrase(credentials.getShaInPassphrase());
        return epdqPayloadDefinition.createGatewayOrder();
    }

    private GatewayOrder buildCancelOrder(CancelGatewayRequest request) {
        EpdqCredentials credentials = (EpdqCredentials) request.getGatewayCredentials();
        var epdqPayloadDefinitionForCancelOrder = new EpdqPayloadDefinitionForCancelOrder();
        Optional.ofNullable(request.getTransactionId())
                .ifPresentOrElse(
                        epdqPayloadDefinitionForCancelOrder::setPayId,
                        () -> epdqPayloadDefinitionForCancelOrder.setOrderId(request.getExternalChargeId()));
        epdqPayloadDefinitionForCancelOrder.setUserId(credentials.getUsername());
        epdqPayloadDefinitionForCancelOrder.setPassword(credentials.getPassword());
        epdqPayloadDefinitionForCancelOrder.setPspId(credentials.getMerchantId());
        epdqPayloadDefinitionForCancelOrder.setShaInPassphrase(credentials.getShaInPassphrase());
        return epdqPayloadDefinitionForCancelOrder.createGatewayOrder();
    }
}
