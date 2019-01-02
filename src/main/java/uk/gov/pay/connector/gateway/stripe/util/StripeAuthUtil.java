package uk.gov.pay.connector.gateway.stripe.util;

import uk.gov.pay.connector.app.StripeAuthTokens;
import uk.gov.pay.connector.app.StripeGatewayConfig;

import static org.apache.commons.lang3.StringUtils.isBlank;

public class StripeAuthUtil {

    public static String getAuthHeaderValue(StripeGatewayConfig stripeGatewayConfig, boolean isLiveAccount) throws NoLiveTokenConfiguredException {
        String token = "Bearer ";
        StripeAuthTokens authTokens = stripeGatewayConfig.getAuthTokens();
        return isLiveAccount ? token + liveToken(authTokens) : token + authTokens.getTest();
    }

    private static String liveToken(StripeAuthTokens authTokens) throws NoLiveTokenConfiguredException {
        if (authTokens.getLive().equals("${GDS_CONNECTOR_STRIPE_AUTH_LIVE_TOKEN}") || isBlank(authTokens.getLive()))
            throw new NoLiveTokenConfiguredException();
        return authTokens.getLive();
    }

}
