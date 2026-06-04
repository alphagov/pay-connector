package uk.gov.pay.connector.gateway.adyen.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.GatewayException.GatewayErrorException;
import uk.gov.pay.connector.gateway.RefundHandler;
import uk.gov.pay.connector.gateway.adyen.AdyenRequestFactory;
import uk.gov.pay.connector.gateway.adyen.request.AdyenRefundRequest;
import uk.gov.pay.connector.gateway.adyen.response.AdyenRefundResponse;
import uk.gov.pay.connector.gateway.adyen.response.json.AdyenError;
import uk.gov.pay.connector.gateway.adyen.response.json.RefundResponseBody;
import uk.gov.pay.connector.gateway.adyen.utils.AdyenRequestUtil;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse;
import uk.gov.pay.connector.util.JsonObjectMapper;

import static net.logstash.logback.argument.StructuredArguments.kv;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static uk.gov.pay.connector.gateway.adyen.utils.AdyenRequestUtil.*;
import static uk.gov.pay.connector.gateway.model.GatewayError.genericGatewayError;
import static uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse.RefundState.ERROR;
import static uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse.RefundState.PENDING;
import static uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse.fromBaseRefundResponse;
import static uk.gov.service.payments.logging.LoggingKeys.GATEWAY_ERROR;
import static uk.gov.service.payments.logging.LoggingKeys.HTTP_STATUS;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;
import static uk.gov.service.payments.logging.LoggingKeys.REFUND_EXTERNAL_ID;

public class AdyenRefundHandler implements RefundHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdyenRefundHandler.class);

    private final GatewayClient gatewayClient;
    private final AdyenGatewayConfig adyenGatewayConfig;
    private final JsonObjectMapper jsonObjectMapper;
    private final AdyenRequestFactory adyenRequestFactory;

    public AdyenRefundHandler(GatewayClient client, ConnectorConfiguration configuration,
                              JsonObjectMapper jsonObjectMapper) {
        this.gatewayClient = client;
        this.adyenGatewayConfig = configuration.getAdyenGatewayConfig();
        this.jsonObjectMapper = jsonObjectMapper;
        this.adyenRequestFactory = new AdyenRequestFactory(configuration);
    }

    public GatewayRefundResponse refund(RefundGatewayRequest request) {
        var adyenRefundRequest = new AdyenRefundRequest(
                getRefundUrl(adyenGatewayConfig, request),
                getHeaders(adyenGatewayConfig, request.getGatewayAccount().isLive()),
                adyenRequestFactory.createRefundRequestPayload(request),
                request.getGatewayAccount().getType(),
                jsonObjectMapper);

        try {
            var jsonResponse = gatewayClient.postRequestFor(adyenRefundRequest).getEntity();
            var adyenRefund = jsonObjectMapper.getObject(jsonResponse, RefundResponseBody.class);

            return fromBaseRefundResponse(AdyenRefundResponse.from(adyenRefund), PENDING);
        } catch (GatewayErrorException e) {
            return handleGatewayErrorException(request, e);
        } catch (GatewayException e) {
            LOGGER.error("Refund failed for transaction",
                    kv(PAYMENT_EXTERNAL_ID, request.getChargeExternalId()),
                    kv(REFUND_EXTERNAL_ID, request.getRefundExternalId()),
                    kv(GATEWAY_ERROR, e.getMessage()));
            return GatewayRefundResponse.fromGatewayError(e.toGatewayError());
        }
    }

    private GatewayRefundResponse handleGatewayErrorException(RefundGatewayRequest request, GatewayErrorException e) {
        try {
            AdyenError adyenError = jsonObjectMapper.getObject(e.getResponseFromGateway(), AdyenError.class);

            LOGGER.warn("Refund failed for transaction",
                    kv(PAYMENT_EXTERNAL_ID, request.getChargeExternalId()),
                    kv(REFUND_EXTERNAL_ID, request.getRefundExternalId()),
                    kv(HTTP_STATUS, e.getStatus()),
                    kv(GATEWAY_ERROR, e.getMessage())
            );
            if (adyenError != null && isNotBlank(adyenError.errorCode())) {
                return fromBaseRefundResponse(AdyenRefundResponse.from(adyenError), ERROR);
            } else {
                GatewayError gatewayError = genericGatewayError(
                        "An internal server error occurred when refunding charge charge_external_id: "
                                + request.getChargeExternalId());
                return GatewayRefundResponse.fromGatewayError(gatewayError);
            }
        } catch (Exception _) {
            LOGGER.warn("Failed to deserialise Adyen error during refund",
                    kv(PAYMENT_EXTERNAL_ID, request.getChargeExternalId()),
                    kv(REFUND_EXTERNAL_ID, request.getRefundExternalId()),
                    kv(GATEWAY_ERROR, e.getMessage())
            );
            return GatewayRefundResponse.fromGatewayError(e.toGatewayError());
        }
    }
}
