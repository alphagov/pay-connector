package uk.gov.pay.connector.gateway.adyen.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.gateway.CaptureHandler;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.GatewayException.GatewayErrorException;
import uk.gov.pay.connector.gateway.adyen.AdyenRequestFactory;
import uk.gov.pay.connector.gateway.adyen.request.AdyenCaptureRequest;
import uk.gov.pay.connector.gateway.adyen.response.AdyenCaptureResponse;
import uk.gov.pay.connector.gateway.adyen.response.json.AdyenError;
import uk.gov.pay.connector.gateway.adyen.response.json.CaptureResponseBody;
import uk.gov.pay.connector.gateway.adyen.utils.AdyenRequestUtil;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.util.JsonObjectMapper;

import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.pay.connector.gateway.CaptureResponse.ChargeState.PENDING;
import static uk.gov.pay.connector.gateway.CaptureResponse.fromBaseCaptureResponse;
import static uk.gov.pay.connector.gateway.model.OrderRequestType.CAPTURE;
import static uk.gov.service.payments.logging.LoggingKeys.GATEWAY_ERROR;
import static uk.gov.service.payments.logging.LoggingKeys.HTTP_STATUS;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;
import static uk.gov.service.payments.logging.LoggingKeys.PROVIDER_PAYMENT_ID;

public class AdyenCaptureHandler implements CaptureHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdyenCaptureHandler.class);

    private final GatewayClient gatewayClient;
    private final AdyenGatewayConfig adyenGatewayConfig;
    private final AdyenRequestFactory adyenRequestFactory;
    private final JsonObjectMapper jsonObjectMapper;

    public AdyenCaptureHandler(GatewayClient gatewayClient,
                               ConnectorConfiguration connectorConfig,
                               JsonObjectMapper jsonObjectMapper) {
        this.gatewayClient = gatewayClient;
        this.adyenGatewayConfig = connectorConfig.getAdyenGatewayConfig();
        this.jsonObjectMapper = jsonObjectMapper;
        this.adyenRequestFactory = new AdyenRequestFactory(connectorConfig);
    }

    @Override
    public CaptureResponse capture(CaptureGatewayRequest request) {
        String transactionId = request.getGatewayTransactionId();

        var adyenCaptureRequest = new AdyenCaptureRequest(
                AdyenRequestUtil.getCaptureUrl(adyenGatewayConfig, request),
                AdyenRequestUtil.getHeaders(adyenGatewayConfig, request.getGatewayAccount().isLive(), CAPTURE, request.getExternalId()),
                adyenRequestFactory.createCapturePayload(request),
                request.getGatewayAccount().getType(),
                jsonObjectMapper);

        try {
            var jsonResponse = gatewayClient.postRequestFor(adyenCaptureRequest).getEntity();
            var captureResponse = jsonObjectMapper.getObject(jsonResponse, CaptureResponseBody.class);

            return fromBaseCaptureResponse(AdyenCaptureResponse.from(captureResponse), PENDING);
        } catch (GatewayErrorException e) {
            return handleGatewayException(request, e, transactionId);
        } catch (GatewayException.GenericGatewayException
                 | GatewayException.GatewayConnectionTimeoutException e) {
            return CaptureResponse.fromGatewayError(e.toGatewayError());
        }
    }

    private CaptureResponse handleGatewayException(CaptureGatewayRequest request,
                                                   GatewayErrorException e,
                                                   String transactionId) {
        try {
            var jsonResponse = jsonObjectMapper.getObject(e.getResponseFromGateway(), AdyenError.class);
            var adyenErrorResponse = AdyenCaptureResponse.from(jsonResponse);

            LOGGER.warn("Capture failed for transaction",
                    kv(PROVIDER_PAYMENT_ID, transactionId),
                    kv(PAYMENT_EXTERNAL_ID, request.getExternalId()),
                    kv(HTTP_STATUS, adyenErrorResponse.status()),
                    kv(GATEWAY_ERROR, e.getMessage())
            );

            return fromBaseCaptureResponse(adyenErrorResponse, null, transactionId);
        } catch (Exception _) {
            LOGGER.warn("Failed to deserialise Adyen error during capture");
            return CaptureResponse.fromGatewayError(e.toGatewayError());
        }
    }
}
