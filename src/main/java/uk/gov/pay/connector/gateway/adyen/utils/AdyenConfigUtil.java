package uk.gov.pay.connector.gateway.adyen.utils;

import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.app.adyen.HmacKeys;
import uk.gov.pay.connector.app.adyen.WebhookHmacKeys;
import uk.gov.pay.connector.gateway.adyen.webhook.model.AdyenWebhookType;

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

    public static String getHmacKey(AdyenGatewayConfig adyenGatewayConfig, boolean live) {
        WebhookHmacKeys webhookHmacKeys;

        if (live) {
            webhookHmacKeys = adyenGatewayConfig.getHmacKeys().payments().live();
        } else {
            webhookHmacKeys = adyenGatewayConfig.getHmacKeys().payments().test();
        }

        return webhookHmacKeys.getPrimary()
                .orElseThrow(() -> new IllegalStateException("Missing primary Adyen HMAC key"));
    }

    public static String getTokenHmacKey(AdyenGatewayConfig adyenGatewayConfig, boolean live) {
        return getPrimaryHmacKey(adyenGatewayConfig.getHmacKeys().tokens(), live);
    }

    private static String getPrimaryHmacKey(HmacKeys.WebhookHmacKeyPair keyPair, boolean live) {
        WebhookHmacKeys webhookHmacKeys = live ? keyPair.live() : keyPair.test();

        return webhookHmacKeys.getPrimary()
                .orElseThrow(() -> new IllegalStateException("Missing primary Adyen HMAC key"));
    }

    public static String getHmacKeyForWebhookType(AdyenGatewayConfig adyenGatewayConfig,
                                                  AdyenWebhookType webhookType,
                                                  boolean live) {

        return switch (webhookType) {
            case PAYMENTS -> getHmacKey(adyenGatewayConfig, live);
            case TOKENS -> getTokenHmacKey(adyenGatewayConfig, live);
        };
    }
}
