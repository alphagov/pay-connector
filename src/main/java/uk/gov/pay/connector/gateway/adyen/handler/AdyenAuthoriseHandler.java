package uk.gov.pay.connector.gateway.adyen.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.adyen.AdyenRequestFactory;
import uk.gov.pay.connector.gateway.adyen.request.AdyenAuthorisationRequest;
import uk.gov.pay.connector.gateway.adyen.response.AdyenAuthoriseResponse;
import uk.gov.pay.connector.gateway.adyen.response.json.AuthoriseResponseBody;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.util.JsonObjectMapper;

import static uk.gov.pay.connector.gateway.adyen.utils.AdyenRequestUtil.getAuthUrl;
import static uk.gov.pay.connector.gateway.adyen.utils.AdyenRequestUtil.getHeaders;

public class AdyenAuthoriseHandler {

    private static final Logger logger = LoggerFactory.getLogger(AdyenAuthoriseHandler.class);

    private final GatewayClient gatewayClient;
    private final AdyenGatewayConfig adyenGatewayConfig;
    private final AdyenRequestFactory adyenRequestFactory;
    private final JsonObjectMapper jsonObjectMapper;

    public AdyenAuthoriseHandler(GatewayClient gatewayClient,
                                 ConnectorConfiguration connectorConfig,
                                 JsonObjectMapper jsonObjectMapper) {
        this.gatewayClient = gatewayClient;
        this.adyenGatewayConfig = connectorConfig.getAdyenGatewayConfig();
        this.jsonObjectMapper = jsonObjectMapper;
        this.adyenRequestFactory = new AdyenRequestFactory(connectorConfig);
    }

    public GatewayResponse authorise(CardAuthorisationGatewayRequest request) throws
            GatewayException.GatewayErrorException,
            GatewayException.GenericGatewayException,
            GatewayException.GatewayConnectionTimeoutException {

        GatewayResponse.GatewayResponseBuilder<BaseResponse> responseBuilder = GatewayResponse
                .GatewayResponseBuilder
                .responseBuilder();

        logger.info("Calling Adyen for authorisation of charge");
        var authorisationRequest = new AdyenAuthorisationRequest(
                getAuthUrl(adyenGatewayConfig, request),
                getHeaders(adyenGatewayConfig, request.getGatewayAccount().isLive(), request.getRequestType(), request.getGovUkPayPaymentId()),
                request.getGatewayAccount().getType(),
                adyenRequestFactory.createPaymentRequest(request),
                jsonObjectMapper);

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
}
