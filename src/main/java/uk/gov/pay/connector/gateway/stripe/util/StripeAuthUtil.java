package uk.gov.pay.connector.gateway.stripe.util;

import uk.gov.pay.connector.app.StripeAuthTokens;
import uk.gov.pay.connector.app.StripeGatewayConfig;

import static java.lang.String.format;

public class StripeAuthUtil {

    public static String getAuthHeaderValue(StripeGatewayConfig stripeGatewayConfig, boolean isLiveAccount) {
        StripeAuthTokens authTokens = stripeGatewayConfig.getAuthTokens();
        return format("Bearer %s", isLiveAccount ? authTokens.getLive() : authTokens.getTest());
    }
}
