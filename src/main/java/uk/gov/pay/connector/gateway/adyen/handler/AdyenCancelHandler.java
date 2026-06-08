package uk.gov.pay.connector.gateway.adyen.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.adyen.AdyenRequestFactory;
import uk.gov.pay.connector.gateway.adyen.request.AdyenCancelRequest;
import uk.gov.pay.connector.gateway.adyen.response.AdyenCancelResponse;
import uk.gov.pay.connector.gateway.adyen.response.json.AdyenError;
import uk.gov.pay.connector.gateway.adyen.response.json.CancelResponseBody;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder;
import uk.gov.pay.connector.util.JsonObjectMapper;

import static uk.gov.pay.connector.gateway.adyen.utils.AdyenRequestUtil.getCancelUrl;
import static uk.gov.pay.connector.gateway.adyen.utils.AdyenRequestUtil.getHeaders;

public class AdyenCancelHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdyenCancelHandler.class);

    private final GatewayClient gatewayClient;
    private final AdyenGatewayConfig adyenGatewayConfig;
    private final AdyenRequestFactory adyenRequestFactory;
    private final JsonObjectMapper jsonObjectMapper;

    public AdyenCancelHandler(GatewayClient gatewayClient,
                              AdyenGatewayConfig adyenGatewayConfig,
                              AdyenRequestFactory adyenRequestFactory,
                              JsonObjectMapper jsonObjectMapper) {
        this.gatewayClient = gatewayClient;
        this.adyenGatewayConfig = adyenGatewayConfig;
        this.adyenRequestFactory = adyenRequestFactory;
        this.jsonObjectMapper = jsonObjectMapper;
    }

    public GatewayResponse<BaseCancelResponse> cancel(CancelGatewayRequest request) {
        GatewayResponseBuilder<BaseCancelResponse> responseBuilder = GatewayResponseBuilder.responseBuilder();
        var cancelRequest = new AdyenCancelRequest(
                getCancelUrl(adyenGatewayConfig, request),
                getHeaders(adyenGatewayConfig, request.isLiveAccount()),
                adyenRequestFactory.createPaymentCancelRequest(request),
                request.getGatewayAccount().getType(),
                jsonObjectMapper);
        try {
            var jsonResponse = gatewayClient.postRequestFor(cancelRequest).getEntity();
            var cancelResponse = jsonObjectMapper.getObject(jsonResponse, CancelResponseBody.class);

            return responseBuilder.withResponse(AdyenCancelResponse.from(cancelResponse)).build();
        } catch (GatewayException.GatewayErrorException e) {
            return handleGatewayException(e);
        } catch (GatewayException.GenericGatewayException
                 | GatewayException.GatewayConnectionTimeoutException e) {
            return buildGatewayResponseFromError(e.toGatewayError());

        }
    }

    private GatewayResponse buildGatewayResponseFromError(GatewayError gatewayError) {
        return GatewayResponseBuilder.responseBuilder()
                .withGatewayError(gatewayError).build();
    }

    private GatewayResponse handleGatewayException(GatewayException.GatewayErrorException e) {
        try {
            var jsonResponse = jsonObjectMapper.getObject(e.getResponseFromGateway(), AdyenError.class);
            var adyenErrorResponse = AdyenCancelResponse.from(jsonResponse);

            return GatewayResponseBuilder.responseBuilder()
                    .withResponse(adyenErrorResponse).build();
        } catch (Exception _) {
            LOGGER.warn("Failed to deserialise Adyen error during cancel");
            return GatewayResponseBuilder.responseBuilder()
                    .withGatewayError(e.toGatewayError()).build();
        }
    }
}
