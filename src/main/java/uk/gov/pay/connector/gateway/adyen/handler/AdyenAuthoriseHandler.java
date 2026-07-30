package uk.gov.pay.connector.gateway.adyen.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.adyen.AdyenRequestFactory;
import uk.gov.pay.connector.gateway.adyen.AuthoriseRequestPayloadToGatewayOrderConverter;
import uk.gov.pay.connector.gateway.adyen.request.AdyenAuthorisationRequest;
import uk.gov.pay.connector.gateway.adyen.request.json.AuthoriseRequestPayload;
import uk.gov.pay.connector.gateway.adyen.response.AdyenAuthoriseResponse;
import uk.gov.pay.connector.gateway.adyen.response.json.AuthoriseResponseBody;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RecurringPaymentAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
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
    private final JsonObjectMapper jsonObjectMapper;

    public AdyenAuthoriseHandler(GatewayClient gatewayClient,
                                 ConnectorConfiguration connectorConfig,
                                 JsonObjectMapper jsonObjectMapper) {
        this.gatewayClient = gatewayClient;
        this.adyenGatewayConfig = connectorConfig.getAdyenGatewayConfig();
        this.jsonObjectMapper = jsonObjectMapper;
        this.adyenRequestFactory = new AdyenRequestFactory(connectorConfig);
        this.authoriseRequestPayloadToGatewayOrderConverter = new AuthoriseRequestPayloadToGatewayOrderConverter(jsonObjectMapper);
    }
    
    public GatewayResponse authorise(CardAuthorisationGatewayRequest request) throws
            GatewayException.GatewayErrorException,
            GatewayException.GenericGatewayException,
            GatewayException.GatewayConnectionTimeoutException {

        GatewayResponse.GatewayResponseBuilder<BaseResponse> responseBuilder = GatewayResponse
                .GatewayResponseBuilder
                .responseBuilder();

        AuthoriseRequestPayload authoriseRequestPayload = adyenRequestFactory.createPaymentRequest(request);
        GatewayOrder gatewayOrder = authoriseRequestPayloadToGatewayOrderConverter.convert(authoriseRequestPayload);

        var authorisationRequest = new AdyenAuthorisationRequest(
                getAuthUrl(adyenGatewayConfig, request),
                getHeaders(adyenGatewayConfig, request.getGatewayAccount().isLive(), AUTHORISE, request.getGovUkPayPaymentId()),
                request.getGatewayAccount().getType(),
                gatewayOrder);

        logger.info("Calling Adyen for authorisation of charge");
        try {
            var jsonResponse = gatewayClient.postRequestFor(authorisationRequest).getEntity();
            var paymentResponse = jsonObjectMapper.getObject(
                    jsonResponse,
                    AuthoriseResponseBody.class);

            return responseBuilder
                    .withResponse(AdyenAuthoriseResponse.of(paymentResponse))
                    .build();
        } catch (GatewayException e) {
            logger.error("GatewayException occurred when authorising payment", e);
            return responseBuilder.withGatewayError(e.toGatewayError()).build();
        }
    }
    
    public GatewayResponse authoriseUserNotPresent(RecurringPaymentAuthorisationGatewayRequest request) {
        GatewayResponse.GatewayResponseBuilder<BaseResponse> responseBuilder = GatewayResponse
                .GatewayResponseBuilder
                .responseBuilder();

        AuthoriseRequestPayload authoriseRequestPayload = adyenRequestFactory.createRecurringPaymentRequest(request);
        GatewayOrder gatewayOrder = authoriseRequestPayloadToGatewayOrderConverter.convert(authoriseRequestPayload);

        var authorisationRequest = new AdyenAuthorisationRequest(
                getAuthUrl(adyenGatewayConfig, request),
                getHeaders(adyenGatewayConfig, request.getGatewayAccount().isLive(), AUTHORISE, request.getGovUkPayPaymentId()),
                request.getGatewayAccount().getType(),
                gatewayOrder);

        logger.info("Calling Adyen for user-not-present authorisation of charge");
        try {
            var jsonResponse = gatewayClient.postRequestFor(authorisationRequest).getEntity();
            var paymentResponse = jsonObjectMapper.getObject(
                    jsonResponse,
                    AuthoriseResponseBody.class);

            return responseBuilder
                    .withResponse(AdyenAuthoriseResponse.of(paymentResponse))
                    .build();
        } catch (GatewayException e) {
            logger.error("GatewayException occurred when authorising user not present payment", e);
            return responseBuilder.withGatewayError(e.toGatewayError()).build();
        }
    }
}
