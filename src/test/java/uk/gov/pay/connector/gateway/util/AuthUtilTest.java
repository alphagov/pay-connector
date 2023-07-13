package uk.gov.pay.connector.gateway.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.gatewayaccountcredentials.exception.MissingCredentialsForRecurringPaymentException;
import uk.gov.service.payments.commons.model.AuthorisationMode;

import java.util.Base64;
import java.util.Map;

import static javax.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_CODE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.RECURRING_MERCHANT_INITIATED;

@ExtendWith(MockitoExtension.class)
class AuthUtilTest {
    private final String merchantCode = "MERCHANTCODE";
    private final String username = "worldpay-username";
    private final String password = "password"; //pragma: allowlist secret
    private final Map<String, Object> worldpayRecurringCredentials = Map.of(
            CREDENTIALS_MERCHANT_ID, merchantCode,
            CREDENTIALS_USERNAME, username,
            CREDENTIALS_PASSWORD, password,
            RECURRING_MERCHANT_INITIATED, Map.of(
                    CREDENTIALS_MERCHANT_CODE, "RC-" + merchantCode,
                    CREDENTIALS_USERNAME, "RC-" + username,
                    CREDENTIALS_PASSWORD, "RC-" + password
            ));

    @Test
    void shouldThrowException_whenAuthModeAgreement_andNoCredentials() {
        MissingCredentialsForRecurringPaymentException thrown = assertThrows(MissingCredentialsForRecurringPaymentException.class, () -> {
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
        String expectedHeader = "Basic " + Base64.getEncoder().encodeToString(new String("RC-" + username + ":" + "RC-" + password).getBytes());
        Map<String, String> encodedHeader = AuthUtil.getGatewayAccountCredentialsAsAuthHeader(worldpayRecurringCredentials, AuthorisationMode.AGREEMENT);
        assertThat(encodedHeader.get(AUTHORIZATION), is(expectedHeader));
    }

    @Test
    void shouldRetrieveTheRightCredentialsForNonRecurringPayments() {
        String expectedHeader = "Basic " + Base64.getEncoder().encodeToString(new String(username + ":" + password).getBytes());
        Map<String, String> encodedHeader = AuthUtil.getGatewayAccountCredentialsAsAuthHeader(worldpayRecurringCredentials, AuthorisationMode.WEB);
        assertThat(encodedHeader.get(AUTHORIZATION), is(expectedHeader));
    }

    @Test
    void shouldRetrieveTheRightCredentialsForManagingTokens() {
        String expectedHeader = "Basic " + Base64.getEncoder().encodeToString(new String(username + ":" + password).getBytes());
        Map<String, String> encodedHeader = AuthUtil.getGatewayAccountCredentialsForManagingTokensAsAuthHeader(worldpayRecurringCredentials);
        assertThat(encodedHeader.get(AUTHORIZATION), is(expectedHeader));
    }

    @Test
    void shouldThrowException_whenAuthModeAgreement_andNoCredentialsForMerchantId() {
        MissingCredentialsForRecurringPaymentException thrown = assertThrows(MissingCredentialsForRecurringPaymentException.class, () -> {
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
        String merchantId = AuthUtil.getWorldpayMerchantCode(worldpayRecurringCredentials, AuthorisationMode.AGREEMENT);
        assertThat(merchantId, is("RC-" + merchantCode));
    }

    @Test
    void shouldRetrieveTheRightMerchantIdForNonRecurringPayments() {
        String merchantId = AuthUtil.getWorldpayMerchantCode(worldpayRecurringCredentials, AuthorisationMode.WEB);
        assertThat(merchantId, is(merchantCode));
    }

    @Test
    void shouldRetrieveTheRightMerchantIdForManagingTokens() {
        String merchantId = AuthUtil.getWorldpayMerchantCodeForManagingTokens(worldpayRecurringCredentials);
        assertThat(merchantId, is(merchantCode));
    }

}
