package uk.gov.pay.connector.gateway.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.gatewayaccount.model.WorldpayCredentials;
import uk.gov.pay.connector.gatewayaccount.model.WorldpayMerchantCodeCredentials;
import uk.gov.pay.connector.gatewayaccountcredentials.exception.MissingCredentialsForRecurringPaymentException;
import uk.gov.service.payments.commons.model.AuthorisationMode;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static javax.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class AuthUtilTest {
    private final String oneOffMerchantCode = "one-off-merchant-code";
    private final String oneOffUsername = "one-off-username";
    private final String oneOffPassword = "one-off-password";
    private final String citMerchantCode = "cit-merchant-code";
    private final String citUsername = "cit-username";
    private final String citPassword = "cit-password";
    private final String mitMerchantCode = "mit-merchant-code";
    private final String mitUsername = "mit-username";
    private final String mitPassword = "mit-password";

    @Test
    void getWorldpayAuthHeader_shouldThrowExceptionWhenNoCredentials_forRecurringCustomerInitiated() {
        WorldpayCredentials credentials = new WorldpayCredentials();
        credentials.setOneOffCustomerInitiatedCredentials(new WorldpayMerchantCodeCredentials(oneOffMerchantCode, oneOffUsername, oneOffPassword));
        credentials.setRecurringMerchantInitiatedCredentials(new WorldpayMerchantCodeCredentials(oneOffMerchantCode, oneOffUsername, oneOffPassword));
        MissingCredentialsForRecurringPaymentException thrown = assertThrows(MissingCredentialsForRecurringPaymentException.class, () -> {
            AuthUtil.getWorldpayAuthHeader(credentials, AuthorisationMode.WEB, true);
        });
    }

    @Test
    void getWorldpayAuthHeader_shouldThrowExceptionWhenNoCredentials_forRecurringMerchantInitiated() {
        WorldpayCredentials credentials = new WorldpayCredentials();
        credentials.setOneOffCustomerInitiatedCredentials(new WorldpayMerchantCodeCredentials(oneOffMerchantCode, oneOffUsername, oneOffPassword));
        credentials.setRecurringCustomerInitiatedCredentials(new WorldpayMerchantCodeCredentials(oneOffMerchantCode, oneOffUsername, oneOffPassword));
        MissingCredentialsForRecurringPaymentException thrown = assertThrows(MissingCredentialsForRecurringPaymentException.class, () -> {
            AuthUtil.getWorldpayAuthHeader(credentials, AuthorisationMode.AGREEMENT, true);
        });
    }

    @Test
    void shouldGetAuthHeaderForRecurringCustomerInitiatedPayment() {
        WorldpayCredentials credentials = getWorldpayCredentialsWithAllPaymentChannels();
        Map<String, String> encodedHeader = AuthUtil.getWorldpayAuthHeader(credentials, AuthorisationMode.WEB, true);

        String expectedHeader = "Basic " + Base64.getEncoder().encodeToString(new String(citUsername + ":" + citPassword).getBytes(StandardCharsets.UTF_8));
        assertThat(encodedHeader.get(AUTHORIZATION), is(expectedHeader));
    }

    @Test
    void shouldGetAuthHeaderForRecurringMerchantInitiatedPayment() {
        WorldpayCredentials credentials = getWorldpayCredentialsWithAllPaymentChannels();
        Map<String, String> encodedHeader = AuthUtil.getWorldpayAuthHeader(credentials, AuthorisationMode.AGREEMENT, true);

        String expectedHeader = "Basic " + Base64.getEncoder().encodeToString(new String(mitUsername + ":" + mitPassword).getBytes(StandardCharsets.UTF_8));
        assertThat(encodedHeader.get(AUTHORIZATION), is(expectedHeader));
    }

    @Test
    void shouldGetAuthHeaderForOneOffPayment() {
        WorldpayCredentials credentials = getWorldpayCredentialsWithAllPaymentChannels();

        Map<String, String> encodedHeader = AuthUtil.getWorldpayAuthHeader(credentials, AuthorisationMode.WEB, false);
        String expectedHeader = "Basic " + Base64.getEncoder().encodeToString(new String(oneOffUsername + ":" + oneOffPassword).getBytes(StandardCharsets.UTF_8));
        assertThat(encodedHeader.get(AUTHORIZATION), is(expectedHeader));
    }

    @Test
    void shouldGetAuthHeaderForManagingTokens() {
        WorldpayCredentials credentials = getWorldpayCredentialsWithAllPaymentChannels();
        String expectedHeader = "Basic " + Base64.getEncoder().encodeToString(new String(citUsername + ":" + citPassword).getBytes(StandardCharsets.UTF_8));
        Map<String, String> encodedHeader = AuthUtil.getWorldpayAuthHeaderForManagingRecurringAuthTokens(credentials);
        assertThat(encodedHeader.get(AUTHORIZATION), is(expectedHeader));
    }

    @Test
    void getWorldpayMerchantCode_shouldThrowExceptionWhenNoCredentials_forRecurringCustomerInitiated() {
        WorldpayCredentials credentials = new WorldpayCredentials();
        credentials.setOneOffCustomerInitiatedCredentials(new WorldpayMerchantCodeCredentials(oneOffMerchantCode, oneOffUsername, oneOffPassword));
        credentials.setRecurringMerchantInitiatedCredentials(new WorldpayMerchantCodeCredentials(oneOffMerchantCode, oneOffUsername, oneOffPassword));
        MissingCredentialsForRecurringPaymentException thrown = assertThrows(MissingCredentialsForRecurringPaymentException.class, () -> {
            AuthUtil.getWorldpayMerchantCode(credentials, AuthorisationMode.WEB, true);
        });
    }

    @Test
    void getWorldpayMerchantCode_shouldThrowExceptionWhenNoCredentials_forRecurringMerchantInitiated() {
        WorldpayCredentials credentials = new WorldpayCredentials();
        credentials.setOneOffCustomerInitiatedCredentials(new WorldpayMerchantCodeCredentials(oneOffMerchantCode, oneOffUsername, oneOffPassword));
        credentials.setRecurringCustomerInitiatedCredentials(new WorldpayMerchantCodeCredentials(oneOffMerchantCode, oneOffUsername, oneOffPassword));
        MissingCredentialsForRecurringPaymentException thrown = assertThrows(MissingCredentialsForRecurringPaymentException.class, () -> {
            AuthUtil.getWorldpayMerchantCode(credentials, AuthorisationMode.AGREEMENT, true);
        });
    }

    @Test
    void shouldGetMerchantCodeForRecurringCustomerInitiatedPayment() {
        WorldpayCredentials credentials = getWorldpayCredentialsWithAllPaymentChannels();
        String merchantId = AuthUtil.getWorldpayMerchantCode(credentials, AuthorisationMode.WEB, true);
        assertThat(merchantId, is(citMerchantCode));
    }

    @Test
    void shouldGetMerchantCodeForRecurringMerchantInitiatedPayment() {
        WorldpayCredentials credentials = getWorldpayCredentialsWithAllPaymentChannels();
        String merchantId = AuthUtil.getWorldpayMerchantCode(credentials, AuthorisationMode.AGREEMENT, true);
        assertThat(merchantId, is(mitMerchantCode));
    }

    @Test
    void shouldGetMerchantCodeForOneOffPayment_whenOneOffCustomerInitiatedCredentialsSet() {
        WorldpayCredentials credentials = getWorldpayCredentialsWithAllPaymentChannels();

        String merchantId = AuthUtil.getWorldpayMerchantCode(credentials, AuthorisationMode.WEB, false);
        assertThat(merchantId, is(oneOffMerchantCode));
    }

    private WorldpayCredentials getWorldpayCredentialsWithAllPaymentChannels() {
        WorldpayCredentials worldpayCredentials = new WorldpayCredentials();
        worldpayCredentials.setOneOffCustomerInitiatedCredentials(new WorldpayMerchantCodeCredentials(oneOffMerchantCode, oneOffUsername, oneOffPassword));
        worldpayCredentials.setRecurringCustomerInitiatedCredentials(new WorldpayMerchantCodeCredentials(citMerchantCode, citUsername, citPassword));
        worldpayCredentials.setRecurringMerchantInitiatedCredentials(new WorldpayMerchantCodeCredentials(mitMerchantCode, mitUsername, mitPassword));
        return worldpayCredentials;
    }

}
