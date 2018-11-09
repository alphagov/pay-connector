package uk.gov.pay.connector.gateway.stripe.util;

import uk.gov.pay.connector.app.StripeGatewayConfig;

public class StripeAuthUtil {

    private StripeAuthUtil() {
    }

    public static String getAuthHeaderValue(StripeGatewayConfig stripeGatewayConfig) {
        return "Bearer " + stripeGatewayConfig.getAuthToken();
    }

}
