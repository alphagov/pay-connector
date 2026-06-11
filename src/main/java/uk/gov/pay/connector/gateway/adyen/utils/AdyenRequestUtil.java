package uk.gov.pay.connector.gateway.adyen.utils;

import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.gateway.GatewayOperation;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.GatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;

import java.net.URI;
import java.util.Map;

import static java.lang.String.format;
import static uk.gov.pay.connector.gateway.adyen.utils.AdyenConfigUtil.getBaseCheckoutUrl;
import static uk.gov.pay.connector.gateway.adyen.utils.AdyenConfigUtil.getCompanyApiKey;

public class AdyenRequestUtil {

    private AdyenRequestUtil() {
    }

    public static URI getAuthUrl(AdyenGatewayConfig config, CardAuthorisationGatewayRequest request) {
        return getUrl(config, request, "/payments");
    }
    
    public static URI get3dsAuthUrl(AdyenGatewayConfig config, Auth3dsResponseGatewayRequest request) {
        return getUrl(config, request, "/payments/details");
    }
    
    public static URI getRefundUrl(AdyenGatewayConfig config, RefundGatewayRequest request) {
        var path =  "/payments/%s/refunds".formatted(request.getTransactionId());
        return getUrl(config, request, path);
    }

    public static URI getCancelUrl(AdyenGatewayConfig config, CancelGatewayRequest request) {
        var path = "/payments/%s/cancels".formatted(request.getTransactionId());
        return getUrl(config, request, path);
    }

    public static URI getCaptureUrl(AdyenGatewayConfig config, CaptureGatewayRequest request) {
        var path = "/payments/%s/captures".formatted(request.getGatewayTransactionId());
        return getUrl(config, request, path);
    }

    private static URI getUrl(AdyenGatewayConfig config, GatewayRequest request, String path) {
        return URI.create(getBaseCheckoutUrl(config, request.getGatewayAccount().isLive()) + path);
    }

    public static Map<String, String> getHeaders(AdyenGatewayConfig config, boolean isLive, GatewayOperation requestType, String idempotencyKey) {
        return Map.of("X-API-Key", getCompanyApiKey(config, isLive),
                "Idempotency-Key", format("%s-%s", requestType.getConfigKey(), idempotencyKey));
    }
}
