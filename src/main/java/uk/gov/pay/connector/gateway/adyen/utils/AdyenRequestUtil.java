package uk.gov.pay.connector.gateway.adyen.utils;

import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;

import java.net.URI;
import java.util.Map;

import static uk.gov.pay.connector.gateway.adyen.utils.AdyenConfigUtil.getBaseCheckoutUrl;
import static uk.gov.pay.connector.gateway.adyen.utils.AdyenConfigUtil.getCompanyApiKey;

public class AdyenRequestUtil {

    public static URI getAuthUrl(AdyenGatewayConfig adyenGatewayConfig, CardAuthorisationGatewayRequest request) {
        return URI.create(getBaseCheckoutUrl(adyenGatewayConfig, request.getGatewayAccount().isLive()) + "/payments");
    }

    public static Map<String, String> getHeaders(AdyenGatewayConfig adyenGatewayConfig, CardAuthorisationGatewayRequest request) {
        return Map.of("X-API-Key",
                getCompanyApiKey(
                        adyenGatewayConfig,
                        request.getGatewayAccount().isLive()
                ));
    }
}
