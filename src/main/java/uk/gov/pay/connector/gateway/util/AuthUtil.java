package uk.gov.pay.connector.gateway.util;

import com.google.common.collect.ImmutableMap;
import uk.gov.pay.connector.app.StripeAuthTokens;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gatewayaccount.model.WorldpayValidatableCredentials;
import uk.gov.pay.connector.gatewayaccountcredentials.exception.MissingCredentialsForRecurringPaymentException;
import uk.gov.service.payments.commons.model.AuthorisationMode;

import java.util.Base64;
import java.util.Map;

import static java.lang.String.format;
import static javax.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_CODE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.RECURRING_MERCHANT_INITIATED;

public class AuthUtil {
    private static final String STRIPE_VERSION_HEADER = "Stripe-Version";
    private static final String STRIPE_API_VERSION = "2019-05-16";

    // We are using a separate version as searching payment intents is only supported in newer API versions.
    // This is intended as a temporary solution, and we intend to move all requests to use the same API version.
    public static final String STRIPE_SEARCH_PAYMENT_INTENTS_API_VERSION = "2020-08-27";

    private static String encode(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString(new String(username + ":" + password).getBytes());
    }

    public static Map<String, String> getStripeAuthHeaderForPaymentIntentSearch(StripeGatewayConfig stripeGatewayConfig, boolean isLiveAccount) {
        return getStripeAuthHeaderWithApiVersion(stripeGatewayConfig, isLiveAccount, STRIPE_SEARCH_PAYMENT_INTENTS_API_VERSION);
    }

    public static Map<String, String> getStripeAuthHeader(StripeGatewayConfig stripeGatewayConfig, boolean isLiveAccount) {
        return getStripeAuthHeaderWithApiVersion(stripeGatewayConfig, isLiveAccount, STRIPE_API_VERSION);
    }

    private static Map<String, String> getStripeAuthHeaderWithApiVersion(StripeGatewayConfig stripeGatewayConfig, boolean isLiveAccount, String apiVersion) {
        StripeAuthTokens authTokens = stripeGatewayConfig.getAuthTokens();
        String value = format("Bearer %s", isLiveAccount ? authTokens.getLive() : authTokens.getTest());
        return ImmutableMap.of(
                AUTHORIZATION, value,
                STRIPE_VERSION_HEADER, apiVersion
        );
    }

    public static String getWorldpayMerchantCode(Map<String, Object> gatewayCredentials, AuthorisationMode authorisationMode) {
        if (authorisationMode == AuthorisationMode.AGREEMENT) {
            if (gatewayCredentials.get(RECURRING_MERCHANT_INITIATED) == null) {
                throw new MissingCredentialsForRecurringPaymentException();
            }
            Map<String, Object> recurringCreds = (Map<String, Object>) gatewayCredentials.get(RECURRING_MERCHANT_INITIATED);

            return recurringCreds.get(CREDENTIALS_MERCHANT_CODE).toString();
        }
        return gatewayCredentials.get(CREDENTIALS_MERCHANT_ID).toString();
    }

    public static String getWorldpayMerchantCodeForManagingTokens(Map<String, Object> gatewayCredentials) {
        return gatewayCredentials.get(CREDENTIALS_MERCHANT_ID).toString();
    }

    public static Map<String, String> getGatewayAccountCredentialsAsAuthHeader(Map<String, Object> gatewayCredentials) {
        String value = encode(gatewayCredentials.get(CREDENTIALS_USERNAME).toString(), gatewayCredentials.get(CREDENTIALS_PASSWORD).toString());
        return ImmutableMap.of(AUTHORIZATION, value);
    }

    public static Map<String, String> getGatewayAccountCredentialsAsAuthHeader(Map<String, Object> gatewayCredentials, AuthorisationMode authorisationMode) {
        if (authorisationMode == AuthorisationMode.AGREEMENT) {
            if (gatewayCredentials.get(RECURRING_MERCHANT_INITIATED) == null) {
                throw new MissingCredentialsForRecurringPaymentException();
            }
            Map<String, Object> recurringCreds = (Map<String, Object>) gatewayCredentials.get(RECURRING_MERCHANT_INITIATED);
            String value = encode(recurringCreds.get(CREDENTIALS_USERNAME).toString(), recurringCreds.get(CREDENTIALS_PASSWORD).toString());
            return ImmutableMap.of(AUTHORIZATION, value);
        }
        String value = encode(gatewayCredentials.get(CREDENTIALS_USERNAME).toString(), gatewayCredentials.get(CREDENTIALS_PASSWORD).toString());
        return ImmutableMap.of(AUTHORIZATION, value);
    }

    public static Map<String, String> getGatewayAccountCredentialsForManagingTokensAsAuthHeader(Map<String, Object> gatewayCredentials) {
        String value = encode(gatewayCredentials.get(CREDENTIALS_USERNAME).toString(), gatewayCredentials.get(CREDENTIALS_PASSWORD).toString());
        return ImmutableMap.of(AUTHORIZATION, value);
    }
    
    public static Map<String, String> getWorldpayCredentialsCheckAuthHeader(WorldpayValidatableCredentials worldpayValidatableCredentials) {
        String value = encode(worldpayValidatableCredentials.getUsername(), worldpayValidatableCredentials.getPassword());
        return ImmutableMap.of(AUTHORIZATION, value);
    }
}
