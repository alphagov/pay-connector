package uk.gov.pay.connector.gateway.adyen.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.GatewayException.GatewayErrorException;
import uk.gov.pay.connector.gateway.adyen.AdyenRequestFactory;
import uk.gov.pay.connector.gateway.adyen.request.AdyenCancelRequest;
import uk.gov.pay.connector.gateway.adyen.response.AdyenCancelResponse;
import uk.gov.pay.connector.gateway.adyen.response.json.AdyenError;
import uk.gov.pay.connector.gateway.adyen.response.json.CancelResponseBody;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder;
import uk.gov.pay.connector.util.JsonObjectMapper;

import static uk.gov.pay.connector.gateway.adyen.utils.AdyenRequestUtil.getCancelUrl;
import static uk.gov.pay.connector.gateway.adyen.utils.AdyenRequestUtil.getHeaders;
import static uk.gov.service.payments.logging.LoggingKeys.GATEWAY_ERROR;
import static uk.gov.service.payments.logging.LoggingKeys.HTTP_STATUS;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;

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
                getHeaders(adyenGatewayConfig, request.isLiveAccount(), request.getRequestType(), request.getExternalChargeId()),
                adyenRequestFactory.createPaymentCancelRequest(request),
                request.getGatewayAccount().getType(),
                jsonObjectMapper);
        try {
            var jsonResponse = gatewayClient.postRequestFor(cancelRequest).getEntity();
            var cancelResponse = jsonObjectMapper.getObject(jsonResponse, CancelResponseBody.class);
            return responseBuilder.withResponse(AdyenCancelResponse.from(cancelResponse))
                    .build();
        } catch (GatewayErrorException e) {
            var adyenError = jsonObjectMapper.getObject(e.getResponseFromGateway(), AdyenError.class);
            LOGGER.atWarn()
                    .setMessage("Cancel failed for transaction")
                    .addKeyValue(PAYMENT_EXTERNAL_ID, request.getExternalChargeId())
                    .addKeyValue(HTTP_STATUS, adyenError.status())
                    .addKeyValue(GATEWAY_ERROR, e.getMessage())
                    .log();
            return responseBuilder.withResponse(AdyenCancelResponse.from(adyenError))
                    .build();
        } catch (GatewayException e) {
            return responseBuilder.withGatewayError(e.toGatewayError()).build();
        }
    }
}
