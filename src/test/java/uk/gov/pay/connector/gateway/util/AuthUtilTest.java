package uk.gov.pay.connector.gateway.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.gatewayaccount.model.WorldpayCredentials;
import uk.gov.pay.connector.gatewayaccount.model.WorldpayMerchantCodeCredentials;
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
    void getWorldpayAuthHeader_shouldThrowExceptionWhenNoCredentials_forRecurringCustomerInitiated() {
        WorldpayCredentials credentials = new WorldpayCredentials();
        credentials.setOneOffCustomerInitiatedCredentials(new WorldpayMerchantCodeCredentials(merchantCode, username, password));
        credentials.setRecurringMerchantInitiatedCredentials(new WorldpayMerchantCodeCredentials(merchantCode, username, password));
        MissingCredentialsForRecurringPaymentException thrown = assertThrows(MissingCredentialsForRecurringPaymentException.class, () -> {
            AuthUtil.getWorldpayAuthHeader(credentials, AuthorisationMode.WEB, true);
        });
    }

    @Test
    void getWorldpayAuthHeader_shouldThrowExceptionWhenNoCredentials_forRecurringMerchantInitiated() {
        WorldpayCredentials credentials = new WorldpayCredentials();
        credentials.setOneOffCustomerInitiatedCredentials(new WorldpayMerchantCodeCredentials(merchantCode, username, password));
        credentials.setRecurringCustomerInitiatedCredentials(new WorldpayMerchantCodeCredentials(merchantCode, username, password));
        MissingCredentialsForRecurringPaymentException thrown = assertThrows(MissingCredentialsForRecurringPaymentException.class, () -> {
            AuthUtil.getWorldpayAuthHeader(credentials, AuthorisationMode.AGREEMENT, true);
        });
    }

    @Test
    void shouldGetAuthHeaderForRecurringCustomerInitiatedPayment() {
        WorldpayCredentials credentials = new WorldpayCredentials();
        credentials.setRecurringCustomerInitiatedCredentials(new WorldpayMerchantCodeCredentials(merchantCode, username, password));
        Map<String, String> encodedHeader = AuthUtil.getWorldpayAuthHeader(credentials, AuthorisationMode.WEB, true);

        String expectedHeader = "Basic " + Base64.getEncoder().encodeToString(new String( username + ":" + password).getBytes());
        assertThat(encodedHeader.get(AUTHORIZATION), is(expectedHeader));
    }

    @Test
    void shouldGetAuthHeaderForRecurringMerchantInitiatedPayment() {
        WorldpayCredentials credentials = new WorldpayCredentials();
        credentials.setRecurringMerchantInitiatedCredentials(new WorldpayMerchantCodeCredentials(merchantCode, username, password));
        Map<String, String> encodedHeader = AuthUtil.getWorldpayAuthHeader(credentials, AuthorisationMode.AGREEMENT, true);

        String expectedHeader = "Basic " + Base64.getEncoder().encodeToString(new String( username + ":" + password).getBytes());
        assertThat(encodedHeader.get(AUTHORIZATION), is(expectedHeader));
    }

    @Test
    void shouldGetAuthHeaderForOneOffPayment_whenOnlyLegacyCredentialsSet() {
        WorldpayCredentials credentials = new WorldpayCredentials();
        credentials.setLegacyOneOffCustomerInitiatedUsername(username);
        credentials.setLegacyOneOffCustomerInitiatedPassword(password);
        
        Map<String, String> encodedHeader = AuthUtil.getWorldpayAuthHeader(credentials, AuthorisationMode.WEB, false);
        String expectedHeader = "Basic " + Base64.getEncoder().encodeToString(new String(username + ":" + password).getBytes());
        assertThat(encodedHeader.get(AUTHORIZATION), is(expectedHeader));
    }

    @Test
    void shouldGetAuthHeaderForOneOffPayment_whenOneOffCustomerInitiatedCredentialsSet() {
        WorldpayCredentials credentials = new WorldpayCredentials();
        credentials.setOneOffCustomerInitiatedCredentials(new WorldpayMerchantCodeCredentials(merchantCode, username, password));

        Map<String, String> encodedHeader = AuthUtil.getWorldpayAuthHeader(credentials, AuthorisationMode.WEB, false);
        String expectedHeader = "Basic " + Base64.getEncoder().encodeToString(new String(username + ":" + password).getBytes());
        assertThat(encodedHeader.get(AUTHORIZATION), is(expectedHeader));
    }

    @Test
    void shouldGetAuthHeaderForManagingTokens() {
        WorldpayCredentials worldpayCredentials = new WorldpayCredentials();
        worldpayCredentials.setRecurringCustomerInitiatedCredentials(new WorldpayMerchantCodeCredentials(merchantCode, username, password));
        String expectedHeader = "Basic " + Base64.getEncoder().encodeToString(new String(username + ":" + password).getBytes());
        Map<String, String> encodedHeader = AuthUtil.getGatewayAccountCredentialsForManagingTokensAsAuthHeader(worldpayCredentials);
        assertThat(encodedHeader.get(AUTHORIZATION), is(expectedHeader));
    }

    @Test
    void getWorldpayMerchantCode_shouldThrowExceptionWhenNoCredentials_forRecurringCustomerInitiated() {
        WorldpayCredentials credentials = new WorldpayCredentials();
        credentials.setOneOffCustomerInitiatedCredentials(new WorldpayMerchantCodeCredentials(merchantCode, username, password));
        credentials.setRecurringMerchantInitiatedCredentials(new WorldpayMerchantCodeCredentials(merchantCode, username, password));
        MissingCredentialsForRecurringPaymentException thrown = assertThrows(MissingCredentialsForRecurringPaymentException.class, () -> {
            AuthUtil.getWorldpayMerchantCode(credentials, AuthorisationMode.WEB, true);
        });
    }

    @Test
    void getWorldpayMerchantCode_shouldThrowExceptionWhenNoCredentials_forRecurringMerchantInitiated() {
        WorldpayCredentials credentials = new WorldpayCredentials();
        credentials.setOneOffCustomerInitiatedCredentials(new WorldpayMerchantCodeCredentials(merchantCode, username, password));
        credentials.setRecurringCustomerInitiatedCredentials(new WorldpayMerchantCodeCredentials(merchantCode, username, password));
        MissingCredentialsForRecurringPaymentException thrown = assertThrows(MissingCredentialsForRecurringPaymentException.class, () -> {
            AuthUtil.getWorldpayMerchantCode(credentials, AuthorisationMode.AGREEMENT, true);
        });
    }

    @Test
    void shouldGetMerchantCodeForRecurringCustomerInitiatedPayment() {
        WorldpayCredentials credentials = new WorldpayCredentials();
        credentials.setRecurringCustomerInitiatedCredentials(new WorldpayMerchantCodeCredentials(merchantCode, username, password));
        String merchantId = AuthUtil.getWorldpayMerchantCode(credentials, AuthorisationMode.WEB, true);
        assertThat(merchantId, is(merchantCode));
    }

    @Test
    void shouldGetMerchantCodeForRecurringMerchantInitiatedPayment() {
        WorldpayCredentials credentials = new WorldpayCredentials();
        credentials.setRecurringMerchantInitiatedCredentials(new WorldpayMerchantCodeCredentials(merchantCode, username, password));
        String merchantId = AuthUtil.getWorldpayMerchantCode(credentials, AuthorisationMode.AGREEMENT, true);
        assertThat(merchantId, is(merchantCode));
    }

    @Test
    void shouldGetMerchantCodeForOneOffPayment_whenOnlyLegacyCredentialsSet() {
        WorldpayCredentials credentials = new WorldpayCredentials();
        credentials.setLegacyOneOffCustomerInitiatedMerchantCode(merchantCode);

        String merchantId = AuthUtil.getWorldpayMerchantCode(credentials, AuthorisationMode.WEB, false);
        assertThat(merchantId, is(merchantCode));
    }

    @Test
    void shouldGetMerchantCodeForOneOffPayment_whenOneOffCustomerInitiatedCredentialsSet() {
        WorldpayCredentials credentials = new WorldpayCredentials();
        credentials.setOneOffCustomerInitiatedCredentials(new WorldpayMerchantCodeCredentials(merchantCode, username, password));

        String merchantId = AuthUtil.getWorldpayMerchantCode(credentials, AuthorisationMode.WEB, false);
        assertThat(merchantId, is(merchantCode));
    }

}
