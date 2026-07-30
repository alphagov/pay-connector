package uk.gov.pay.connector.gateway.adyen.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.adyen.AdyenAuthoriseRequestToGatewayOrderConverter;
import uk.gov.pay.connector.gateway.adyen.AdyenRequestFactory;
import uk.gov.pay.connector.gateway.adyen.AuthoriseRequestPayloadToGatewayOrderConverter;
import uk.gov.pay.connector.gateway.adyen.request.AdyenAuthorisationRequest;
import uk.gov.pay.connector.gateway.adyen.request.json.AuthoriseRequestPayload;
import uk.gov.pay.connector.gateway.adyen.response.AdyenAuthoriseResponse;
import uk.gov.pay.connector.gateway.adyen.response.json.AuthoriseResponseBody;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RecurringPaymentAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.records.AdyenAuthoriseRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.BaseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.util.JsonObjectMapper;

import static uk.gov.pay.connector.gateway.adyen.utils.AdyenRequestUtil.getAuthUrl;
import static uk.gov.pay.connector.gateway.adyen.utils.AdyenRequestUtil.getHeaders;
import static uk.gov.pay.connector.gateway.model.OrderRequestType.AUTHORISE;

public class AdyenAuthoriseHandler {

    private static final Logger logger = LoggerFactory.getLogger(AdyenAuthoriseHandler.class);

    private final GatewayClient gatewayClient;
    private final AdyenGatewayConfig adyenGatewayConfig;
    private final AdyenRequestFactory adyenRequestFactory;
    private final AuthoriseRequestPayloadToGatewayOrderConverter authoriseRequestPayloadToGatewayOrderConverter;
    private final AdyenAuthoriseRequestToGatewayOrderConverter adyenAuthoriseRequestToGatewayOrderConverter;
    private final JsonObjectMapper jsonObjectMapper;

    public AdyenAuthoriseHandler(GatewayClient gatewayClient,
                                 ConnectorConfiguration connectorConfig,
                                 AdyenAuthoriseRequestToGatewayOrderConverter adyenAuthoriseRequestToGatewayOrderConverter,
                                 JsonObjectMapper jsonObjectMapper) {
        this.gatewayClient = gatewayClient;
        this.adyenGatewayConfig = connectorConfig.getAdyenGatewayConfig();
        this.jsonObjectMapper = jsonObjectMapper;
        this.adyenRequestFactory = new AdyenRequestFactory(connectorConfig);
        this.authoriseRequestPayloadToGatewayOrderConverter = new AuthoriseRequestPayloadToGatewayOrderConverter(jsonObjectMapper);
        this.adyenAuthoriseRequestToGatewayOrderConverter = adyenAuthoriseRequestToGatewayOrderConverter;
    }

    public GatewayResponse<BaseAuthoriseResponse> authorise(CardAuthorisationGatewayRequest request) throws
            GatewayException.GatewayErrorException,
            GatewayException.GenericGatewayException,
            GatewayException.GatewayConnectionTimeoutException {

        AuthoriseRequestPayload authoriseRequestPayload = adyenRequestFactory.createPaymentRequest(request);
        GatewayOrder gatewayOrder = authoriseRequestPayloadToGatewayOrderConverter.convert(authoriseRequestPayload);

        var authorisationRequest = new AdyenAuthorisationRequest(
                getAuthUrl(adyenGatewayConfig, request),
                getHeaders(adyenGatewayConfig, request.getGatewayAccount().isLive(), AUTHORISE, request.getGovUkPayPaymentId()),
                request.getGatewayAccount().getType(),
                gatewayOrder);

        return sendRequestToAdyen(authorisationRequest, false);
    }

    public GatewayResponse<BaseAuthoriseResponse> authorise(AdyenAuthoriseRequest request, GatewayAccountType gatewayAccountType) throws
            GatewayException.GatewayErrorException,
            GatewayException.GenericGatewayException,
            GatewayException.GatewayConnectionTimeoutException {

        GatewayOrder gatewayOrder = adyenAuthoriseRequestToGatewayOrderConverter.convert(request);

        boolean isLive = switch (gatewayAccountType) {
            case LIVE -> true;
            case TEST -> false;
        };

        var authorisationRequest = new AdyenAuthorisationRequest(
                getAuthUrl(adyenGatewayConfig, isLive),
                getHeaders(adyenGatewayConfig, isLive, AUTHORISE, request.reference()),
                gatewayAccountType.toString(),
                gatewayOrder);

        return sendRequestToAdyen(authorisationRequest, false);
    }
    
    public GatewayResponse<BaseAuthoriseResponse> authoriseUserNotPresent(RecurringPaymentAuthorisationGatewayRequest request) {
        AuthoriseRequestPayload authoriseRequestPayload = adyenRequestFactory.createRecurringPaymentRequest(request);
        GatewayOrder gatewayOrder = authoriseRequestPayloadToGatewayOrderConverter.convert(authoriseRequestPayload);

        var authorisationRequest = new AdyenAuthorisationRequest(
                getAuthUrl(adyenGatewayConfig, request),
                getHeaders(adyenGatewayConfig, request.getGatewayAccount().isLive(), AUTHORISE, request.getGovUkPayPaymentId()),
                request.getGatewayAccount().getType(),
                gatewayOrder);

        return sendRequestToAdyen(authorisationRequest, true);
    }

    private GatewayResponse sendRequestToAdyen(AdyenAuthorisationRequest authorisationRequest, boolean userNotPresent) {
        String extraLog =  userNotPresent ? "user-not-present " : "";

        GatewayResponse.GatewayResponseBuilder<BaseResponse> responseBuilder = GatewayResponse
                .GatewayResponseBuilder
                .responseBuilder();

        logger.info("Calling Adyen for " + extraLog + "authorisation of charge");
        try {
            var jsonResponse = gatewayClient.postRequestFor(authorisationRequest).getEntity();
            var paymentResponse = jsonObjectMapper.getObject(
                    jsonResponse,
                    AuthoriseResponseBody.class);

            return responseBuilder
                    .withResponse(AdyenAuthoriseResponse.of(paymentResponse))
                    .build();
        } catch (GatewayException e) {
            logger.error("GatewayException occurred when authorising " + extraLog + "payment", e);
            return responseBuilder.withGatewayError(e.toGatewayError()).build();
        }
    }
}
