package uk.gov.pay.connector.gateway.adyen.utils;

import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;

import java.net.URI;
import java.util.Map;

import static uk.gov.pay.connector.gateway.adyen.utils.AdyenConfigUtil.getBaseCheckoutUrl;
import static uk.gov.pay.connector.gateway.adyen.utils.AdyenConfigUtil.getCompanyApiKey;

public class AdyenRequestUtil {

    private AdyenRequestUtil() {}
    
    public static URI getAuthUrl(AdyenGatewayConfig adyenGatewayConfig, CardAuthorisationGatewayRequest request) {
        return URI.create(getBaseCheckoutUrl(adyenGatewayConfig, request.getGatewayAccount().isLive()) + "/payments");
    }

    public static URI getCaptureUrl(AdyenGatewayConfig adyenGatewayConfig, CaptureGatewayRequest request) {
        return URI.create(getBaseCheckoutUrl(adyenGatewayConfig, request.getGatewayAccount().isLive()) + "/payments/" + request.getGatewayTransactionId() + "/captures");
    }

    public static Map<String, String> getHeaders(AdyenGatewayConfig adyenGatewayConfig, boolean isLive) {
        return Map.of("X-API-Key",
                getCompanyApiKey(
                        adyenGatewayConfig,
                        isLive
                ));
    }
}
