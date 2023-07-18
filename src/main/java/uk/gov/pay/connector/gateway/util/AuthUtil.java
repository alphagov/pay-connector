package uk.gov.pay.connector.gateway.util;

import uk.gov.pay.connector.app.StripeAuthTokens;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gatewayaccount.model.EpdqCredentials;
import uk.gov.pay.connector.gatewayaccount.model.GatewayCredentials;
import uk.gov.pay.connector.gatewayaccount.model.WorldpayCredentials;
import uk.gov.pay.connector.gatewayaccount.model.WorldpayMerchantCodeCredentials;
import uk.gov.pay.connector.gatewayaccount.model.WorldpayValidatableCredentials;
import uk.gov.pay.connector.gatewayaccountcredentials.exception.MissingCredentialsForRecurringPaymentException;
import uk.gov.pay.connector.gatewayaccountcredentials.exception.NoCredentialsInUsableStateException;
import uk.gov.service.payments.commons.model.AuthorisationMode;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static java.lang.String.format;
import static javax.ws.rs.core.HttpHeaders.AUTHORIZATION;

public class AuthUtil {
    private static final String STRIPE_VERSION_HEADER = "Stripe-Version";
    private static final String STRIPE_API_VERSION = "2019-05-16";

    // We are using a separate version as searching payment intents is only supported in newer API versions.
    // This is intended as a temporary solution, and we intend to move all requests to use the same API version.
    public static final String STRIPE_SEARCH_PAYMENT_INTENTS_API_VERSION = "2020-08-27";

    public static Map<String, String> getStripeAuthHeaderForPaymentIntentSearch(StripeGatewayConfig stripeGatewayConfig, boolean isLiveAccount) {
        return getStripeAuthHeaderWithApiVersion(stripeGatewayConfig, isLiveAccount, STRIPE_SEARCH_PAYMENT_INTENTS_API_VERSION);
    }

    public static Map<String, String> getStripeAuthHeader(StripeGatewayConfig stripeGatewayConfig, boolean isLiveAccount) {
        return getStripeAuthHeaderWithApiVersion(stripeGatewayConfig, isLiveAccount, STRIPE_API_VERSION);
    }

    private static Map<String, String> getStripeAuthHeaderWithApiVersion(StripeGatewayConfig stripeGatewayConfig, boolean isLiveAccount, String apiVersion) {
        StripeAuthTokens authTokens = stripeGatewayConfig.getAuthTokens();
        String value = format("Bearer %s", isLiveAccount ? authTokens.getLive() : authTokens.getTest());
        return Map.of(
                AUTHORIZATION, value,
                STRIPE_VERSION_HEADER, apiVersion
        );
    }

    public static String getWorldpayMerchantCode(GatewayCredentials credentials, AuthorisationMode authorisationMode, boolean isRecurring) {
        WorldpayCredentials worldpayCredentials = castGatewayCredentialsToWorldpayCredentials(credentials);
        if (isRecurring) {
            WorldpayMerchantCodeCredentials credentialsForAuthType = getWorldpayRecurringCredentialsForAuthType(worldpayCredentials, authorisationMode);
            return credentialsForAuthType.getMerchantCode();
        }

        return worldpayCredentials.getOneOffCustomerInitiatedCredentials()
                .map(WorldpayMerchantCodeCredentials::getMerchantCode)
                .orElseGet(() -> worldpayCredentials.getLegacyOneOffCustomerInitiatedMerchantCode()
                        .orElseThrow(NoCredentialsInUsableStateException::new));
    }

    public static Map<String, String> getWorldpayAuthHeader(GatewayCredentials credentials, AuthorisationMode authorisationMode, boolean isRecurring) {
        WorldpayCredentials worldpayCredentials = castGatewayCredentialsToWorldpayCredentials(credentials);

        if (isRecurring) {
            WorldpayMerchantCodeCredentials credentialsForAuthType = getWorldpayRecurringCredentialsForAuthType(worldpayCredentials, authorisationMode);
            return getWorldpayAuthHeader(credentialsForAuthType);
        }

        return worldpayCredentials.getOneOffCustomerInitiatedCredentials()
                .map(AuthUtil::getWorldpayAuthHeader)
                .orElseGet(() -> {
                    String username = worldpayCredentials.getLegacyOneOffCustomerInitiatedUsername().orElseThrow(NoCredentialsInUsableStateException::new);
                    String password = worldpayCredentials.getLegacyOneOffCustomerInitiatedPassword().orElseThrow(NoCredentialsInUsableStateException::new);
                    return getAuthHeader(username, password);
                });
    }
    
    private static WorldpayMerchantCodeCredentials getWorldpayRecurringCredentialsForAuthType(WorldpayCredentials worldpayCredentials, AuthorisationMode authorisationMode) {
        if (authorisationMode == AuthorisationMode.AGREEMENT) {
            return worldpayCredentials.getRecurringMerchantInitiatedCredentials()
                    .orElseThrow(MissingCredentialsForRecurringPaymentException::new);
        }
        return worldpayCredentials.getRecurringCustomerInitiatedCredentials()
                .orElseThrow(MissingCredentialsForRecurringPaymentException::new);
    }

    private static Map<String, String> getWorldpayAuthHeader(WorldpayMerchantCodeCredentials creds) {
        return getAuthHeader(creds.getUsername(), creds.getPassword());
    }

    public static Map<String, String> getWorldpayAuthHeaderForManagingRecurringAuthTokens(GatewayCredentials credentials) {
        WorldpayCredentials worldpayCredentials = castGatewayCredentialsToWorldpayCredentials(credentials);
        WorldpayMerchantCodeCredentials recurringCustomerInitiatedCredentials = worldpayCredentials.getRecurringCustomerInitiatedCredentials()
                .orElseThrow(MissingCredentialsForRecurringPaymentException::new);
        return getWorldpayAuthHeader(recurringCustomerInitiatedCredentials);
    }

    private static WorldpayCredentials castGatewayCredentialsToWorldpayCredentials(GatewayCredentials credentials) {
        if (!(credentials instanceof WorldpayCredentials)) {
            throw new IllegalArgumentException("Expected provided GatewayCredentials to be of type WorldpayCredentials");
        }
        return (WorldpayCredentials) credentials;
    }

    public static Map<String, String> getWorldpayCredentialsCheckAuthHeader(WorldpayValidatableCredentials worldpayValidatableCredentials) {
        return getAuthHeader(worldpayValidatableCredentials.getUsername(), worldpayValidatableCredentials.getPassword());
    }

    public static Map<String, String> getEpdqAuthHeader(GatewayCredentials credentials) {
        if (!(credentials instanceof EpdqCredentials)) {
            throw new IllegalArgumentException("Expected provided GatewayCredentials to be of type EpdqCredentials");
        }
        var epdqCredentials = (EpdqCredentials) credentials;
        return getAuthHeader(epdqCredentials.getUsername(), epdqCredentials.getPassword());
    }

    private static Map<String, String> getAuthHeader(String username, String password) {
        String value = "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        return Map.of(AUTHORIZATION, value);
    }

}
