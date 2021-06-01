package uk.gov.pay.connector.gateway.util;

import com.google.common.collect.ImmutableMap;
import uk.gov.pay.connector.app.StripeAuthTokens;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gatewayaccount.model.WorldpayCredentials;

import java.util.Base64;
import java.util.Map;

import static java.lang.String.format;
import static javax.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;

public class AuthUtil {
    private static final String STRIPE_VERSION_HEADER = "Stripe-Version";
    private static final String STRIPE_API_VERSION = "2019-05-16";

    private static String encode(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString(new String(username + ":" + password).getBytes());
    }

    public static Map<String, String> getStripeAuthHeader(StripeGatewayConfig stripeGatewayConfig, boolean isLiveAccount) {
        StripeAuthTokens authTokens = stripeGatewayConfig.getAuthTokens();
        String value = format("Bearer %s", isLiveAccount ? authTokens.getLive() : authTokens.getTest());
        return ImmutableMap.of(
                AUTHORIZATION, value,
                STRIPE_VERSION_HEADER, STRIPE_API_VERSION
        );
    }

    public static Map<String, String> getGatewayAccountCredentialsAsAuthHeader(Map<String, String> gatewayCredentials) {
        String value = encode(gatewayCredentials.get(CREDENTIALS_USERNAME), gatewayCredentials.get(CREDENTIALS_PASSWORD));
        return ImmutableMap.of(AUTHORIZATION, value);
    }
    
    public static Map<String, String> getWorldpayCredentialsCheckAuthHeader(WorldpayCredentials worldpayCredentials) {
        String value = encode(worldpayCredentials.getUsername(), worldpayCredentials.getPassword());
        return ImmutableMap.of(AUTHORIZATION, value);
    }
}
