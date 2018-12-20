package uk.gov.pay.connector.gateway.stripe.util;

import uk.gov.pay.connector.app.StripeGatewayConfig;

public class StripeAuthUtil {

    private StripeAuthUtil() {
    }

    public static String getAuthHeaderValue(StripeGatewayConfig stripeGatewayConfig, boolean isLiveAccount) {
        String token = isLiveAccount ? stripeGatewayConfig.getAuthTokens().getLive() : stripeGatewayConfig.getAuthTokens().getTest();
        return "Bearer " + token;
    }

}
