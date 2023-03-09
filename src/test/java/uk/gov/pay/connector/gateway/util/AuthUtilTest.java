package uk.gov.pay.connector.gateway.util;

import org.junit.Rule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.rules.ExpectedException;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.gatewayaccountcredentials.exception.MissingCredentialsForRecurringPaymentException;
import uk.gov.service.payments.commons.model.AuthorisationMode;

import java.util.Base64;
import java.util.Map;

import static javax.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.RECURRING_MERCHANT_INITIATED;

@ExtendWith(MockitoExtension.class)
class AuthUtilTest {
    private String merchantCode = "MERCHANTCODE";
    private String username = "worldpay-username";
    private String password = "password"; //pragma: allowlist secret

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @Test
    void shouldThrowException_whenAuthModeAgreement_andNoCredentials() {
        MissingCredentialsForRecurringPaymentException thrown = Assertions.assertThrows(MissingCredentialsForRecurringPaymentException.class, () -> {
            Map<String, Object> credentials = Map.of(
                    CREDENTIALS_MERCHANT_ID, merchantCode,
                    CREDENTIALS_USERNAME, username,
                    CREDENTIALS_PASSWORD, password);
            AuthUtil.getGatewayAccountCredentialsAsAuthHeader(credentials, AuthorisationMode.AGREEMENT);
        });
        assertThat(thrown.getMessage(), is("Credentials are missing for merchant initiated recurring payment"));
    }

    @Test
    void shouldRetrieveTheRightCredentialsForRecurringPayments() {
        Map<String, Object> credentials = Map.of(
                CREDENTIALS_MERCHANT_ID, merchantCode,
                CREDENTIALS_USERNAME, username,
                CREDENTIALS_PASSWORD, password,
                RECURRING_MERCHANT_INITIATED, Map.of(
                        CREDENTIALS_MERCHANT_ID, "RC-" + merchantCode,
                        CREDENTIALS_USERNAME, "RC-" + username,
                        CREDENTIALS_PASSWORD, "RC-" + password
                ));
        String expectedHeader = "Basic " + Base64.getEncoder().encodeToString(new String("RC-" + username + ":" + "RC-" + password).getBytes());
        Map<String, String> encodedHeader = AuthUtil.getGatewayAccountCredentialsAsAuthHeader(credentials, AuthorisationMode.AGREEMENT);
        assertThat(encodedHeader.get(AUTHORIZATION), is(expectedHeader));
    }

    @Test
    void shouldRetrieveTheRightCredentialsForNonRecurringPayments() {
        Map<String, Object> credentials = Map.of(
                CREDENTIALS_MERCHANT_ID, merchantCode,
                CREDENTIALS_USERNAME, username,
                CREDENTIALS_PASSWORD, password,
                RECURRING_MERCHANT_INITIATED, Map.of(
                        CREDENTIALS_MERCHANT_ID, "RC-" + merchantCode,
                        CREDENTIALS_USERNAME, "RC-" + username,
                        CREDENTIALS_PASSWORD, "RC-" + password
                ));
        String expectedHeader = "Basic " + Base64.getEncoder().encodeToString(new String(username + ":" + password).getBytes());
        Map<String, String> encodedHeader = AuthUtil.getGatewayAccountCredentialsAsAuthHeader(credentials, AuthorisationMode.WEB);
        assertThat(encodedHeader.get(AUTHORIZATION), is(expectedHeader));
    }

    @Test
    void shouldRetrieveTheRightCredentialsForManagingTokens() {
        Map<String, Object> credentials = Map.of(
                CREDENTIALS_MERCHANT_ID, merchantCode,
                CREDENTIALS_USERNAME, username,
                CREDENTIALS_PASSWORD, password,
                RECURRING_MERCHANT_INITIATED, Map.of(
                        CREDENTIALS_MERCHANT_ID, "RC-" + merchantCode,
                        CREDENTIALS_USERNAME, "RC-" + username,
                        CREDENTIALS_PASSWORD, "RC-" + password
                ));
        String expectedHeader = "Basic " + Base64.getEncoder().encodeToString(new String(username + ":" + password).getBytes());
        Map<String, String> encodedHeader = AuthUtil.getGatewayAccountCredentialsForManagingTokensAsAuthHeader(credentials);
        assertThat(encodedHeader.get(AUTHORIZATION), is(expectedHeader));
    }
    @Test
    void shouldThrowException_whenAuthModeAgreement_andNoCredentialsForMerchantId() {
        MissingCredentialsForRecurringPaymentException thrown = Assertions.assertThrows(MissingCredentialsForRecurringPaymentException.class, () -> {
            Map<String, Object> credentials = Map.of(
                    CREDENTIALS_MERCHANT_ID, merchantCode,
                    CREDENTIALS_USERNAME, username,
                    CREDENTIALS_PASSWORD, password);
            AuthUtil.getWorldpayMerchantCode(credentials, AuthorisationMode.AGREEMENT);
        });
        assertThat(thrown.getMessage(), is("Credentials are missing for merchant initiated recurring payment"));
    }

    @Test
    void shouldRetrieveTheRightMerchantIdForRecurringPayments() {
        Map<String, Object> credentials = Map.of(
                CREDENTIALS_MERCHANT_ID, merchantCode,
                CREDENTIALS_USERNAME, username,
                CREDENTIALS_PASSWORD, password,
                RECURRING_MERCHANT_INITIATED, Map.of(
                        CREDENTIALS_MERCHANT_ID, "RC-" + merchantCode,
                        CREDENTIALS_USERNAME, "RC-" + username,
                        CREDENTIALS_PASSWORD, "RC-" + password
                ));
        String merchantId = AuthUtil.getWorldpayMerchantCode(credentials, AuthorisationMode.AGREEMENT);
        assertThat(merchantId, is("RC-" + merchantCode));
    }

    @Test
    void shouldRetrieveTheRightMerchantIdForNonRecurringPayments() {
        Map<String, Object> credentials = Map.of(
                CREDENTIALS_MERCHANT_ID, merchantCode,
                CREDENTIALS_USERNAME, username,
                CREDENTIALS_PASSWORD, password,
                RECURRING_MERCHANT_INITIATED, Map.of(
                        CREDENTIALS_MERCHANT_ID, "RC-" + merchantCode,
                        CREDENTIALS_USERNAME, "RC-" + username,
                        CREDENTIALS_PASSWORD, "RC-" + password
                ));
        String merchantId = AuthUtil.getWorldpayMerchantCode(credentials, AuthorisationMode.WEB);
        assertThat(merchantId, is(merchantCode));
    }

    @Test
    void shouldRetrieveTheRightMerchantIdForManagingTokens() {
        Map<String, Object> credentials = Map.of(
                CREDENTIALS_MERCHANT_ID, merchantCode,
                CREDENTIALS_USERNAME, username,
                CREDENTIALS_PASSWORD, password,
                RECURRING_MERCHANT_INITIATED, Map.of(
                        CREDENTIALS_MERCHANT_ID, "RC-" + merchantCode,
                        CREDENTIALS_USERNAME, "RC-" + username,
                        CREDENTIALS_PASSWORD, "RC-" + password
                ));
        String merchantId = AuthUtil.getWorldpayMerchantCodeForManagingTokens(credentials);
        assertThat(merchantId, is(merchantCode));
    }

}
