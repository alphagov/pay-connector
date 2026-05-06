package uk.gov.pay.connector.gateway.adyen.utils;

import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;

public class AdyenConfigUtil {

    private AdyenConfigUtil() {}

    public static String getCompanyApiKey(AdyenGatewayConfig adyenGatewayConfig, boolean live) {
        String apiKey;
        if (live) {
            apiKey = adyenGatewayConfig.getApiKeys().companyAccount().live();
        } else {
            apiKey = adyenGatewayConfig.getApiKeys().companyAccount().test();
        }
        return apiKey;
    }

    public static String getBaseCheckoutUrl(AdyenGatewayConfig adyenGatewayConfig, boolean live) {
        String baseCheckoutUrl;
        if (live) {
            baseCheckoutUrl = adyenGatewayConfig.getBaseUrls().checkout().live();
        } else {
            baseCheckoutUrl = adyenGatewayConfig.getBaseUrls().checkout().test();
        }
        return baseCheckoutUrl;
    }

    public static String getMerchantAccountId(AdyenGatewayConfig adyenGatewayConfig, boolean live) {
        String merchantAccountId;
        if (live) {
            merchantAccountId = adyenGatewayConfig.getMerchantAccountIds().live();
        } else {
            merchantAccountId = adyenGatewayConfig.getMerchantAccountIds().test();
        }
        return merchantAccountId;
    }
}
