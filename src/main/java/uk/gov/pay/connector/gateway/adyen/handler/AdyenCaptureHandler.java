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
import uk.gov.pay.connector.gateway.adyen.model.AdyenCaptureRequest;
import uk.gov.pay.connector.gateway.adyen.model.AdyenCaptureResponse;
import uk.gov.pay.connector.gateway.adyen.utils.AdyenRequestUtil;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.util.JsonObjectMapper;

import static jakarta.ws.rs.core.Response.Status.Family.CLIENT_ERROR;
import static jakarta.ws.rs.core.Response.Status.Family.SERVER_ERROR;
import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.pay.connector.gateway.CaptureResponse.ChargeState.PENDING;
import static uk.gov.pay.connector.gateway.CaptureResponse.fromBaseCaptureResponse;
import static uk.gov.pay.connector.gateway.model.GatewayError.gatewayConnectionError;
import static uk.gov.service.payments.logging.LoggingKeys.GATEWAY_ERROR;
import static uk.gov.service.payments.logging.LoggingKeys.HTTP_STATUS;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;
import static uk.gov.service.payments.logging.LoggingKeys.PROVIDER_PAYMENT_ID;

public class AdyenCaptureHandler implements CaptureHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdyenCaptureHandler.class);

    private final GatewayClient gatewayClient;
    private final AdyenGatewayConfig adyenGatewayConfig;
    private final JsonObjectMapper jsonObjectMapper;
    private final AdyenRequestFactory adyenRequestFactory;

    public AdyenCaptureHandler(GatewayClient client, ConnectorConfiguration configuration, JsonObjectMapper jsonObjectMapper) {
        this.gatewayClient = client;
        this.adyenGatewayConfig = configuration.getAdyenGatewayConfig();
        this.jsonObjectMapper = jsonObjectMapper;
        this.adyenRequestFactory = new AdyenRequestFactory(configuration);
    }

    @Override
    public CaptureResponse capture(CaptureGatewayRequest request) {
        String transactionId = request.getGatewayTransactionId();

        var adyenCaptureRequest = new AdyenCaptureRequest(
                AdyenRequestUtil.getCaptureUrl(adyenGatewayConfig, request),
                AdyenRequestUtil.getHeaders(adyenGatewayConfig, request.getGatewayAccount().isLive()),
                adyenRequestFactory.createCapturePayload(request),
                request.getGatewayAccount().getType(),
                jsonObjectMapper);

        try {
            var jsonResponse = gatewayClient.postRequestFor(adyenCaptureRequest).getEntity();
            var captureResponse = jsonObjectMapper.getObject(jsonResponse, AdyenCaptureResponse.class);

            return fromBaseCaptureResponse(captureResponse, PENDING);
        } catch (GatewayErrorException e) {
            return handleGatewayErrorException(request, e, transactionId);
        } catch (GatewayException e) {
            return CaptureResponse.fromGatewayError(e.toGatewayError());
        }
    }

    private CaptureResponse handleGatewayErrorException(CaptureGatewayRequest request, GatewayErrorException e, String transactionId) {
        if (e.getFamily() == CLIENT_ERROR) {
            var adyenErrorResponse = jsonObjectMapper.getObject(e.getResponseFromGateway(), AdyenCaptureResponse.class);
            LOGGER.warn("Capture failed for transaction",
                    kv(PROVIDER_PAYMENT_ID, transactionId),
                    kv(PAYMENT_EXTERNAL_ID, request.getExternalId()),
                    kv(HTTP_STATUS, e.getStatus()),
                    kv(GATEWAY_ERROR, e.getMessage())
            );
            return fromBaseCaptureResponse(adyenErrorResponse, null, transactionId);
        }
        if (e.getFamily() == SERVER_ERROR) {
            LOGGER.warn("Capture failed for transaction",
                    kv(PROVIDER_PAYMENT_ID, transactionId),
                    kv(PAYMENT_EXTERNAL_ID, request.getExternalId()),
                    kv(HTTP_STATUS, e.getStatus()),
                    kv(GATEWAY_ERROR, e.getMessage())
            );
            GatewayError gatewayError = gatewayConnectionError("An internal server error occurred when capturing charge_external_id: "
                    + request.getExternalId());
            return CaptureResponse.fromGatewayError(gatewayError);
        }
        LOGGER.error("Unrecognised response status during capture",
                kv(PAYMENT_EXTERNAL_ID, request.getExternalId()),
                kv(HTTP_STATUS, e.getStatus()),
                kv(GATEWAY_ERROR, e.getResponseFromGateway())
        );
        throw new RuntimeException("Unrecognised response status during capture.");
    }
}
