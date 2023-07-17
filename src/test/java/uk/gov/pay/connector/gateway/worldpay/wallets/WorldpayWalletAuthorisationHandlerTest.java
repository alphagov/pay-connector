package uk.gov.pay.connector.gateway.worldpay.wallets;

import com.amazonaws.util.json.Jackson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException.GatewayErrorException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.PayersCardType;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;
import uk.gov.pay.connector.wallets.WalletAuthorisationGatewayRequest;
import uk.gov.pay.connector.wallets.applepay.AppleDecryptedPaymentData;
import uk.gov.pay.connector.wallets.googlepay.api.GooglePayAuthRequest;
import uk.gov.pay.connector.wallets.model.WalletPaymentInfo;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static io.dropwizard.testing.FixtureHelpers.fixture;
import static javax.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_GOOGLE_PAY_3DS_REQUEST_WITH_DDC_RESULT;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_APPLE_PAY_REQUEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_APPLE_PAY_REQUEST_WITH_EMAIL;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_GOOGLE_PAY_3DS_REQUEST_WITHOUT_IP_ADDRESS;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_GOOGLE_PAY_3DS_REQUEST_WITH_EMAIL;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_GOOGLE_PAY_3DS_REQUEST_WITH_IP_ADDRESS;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_GOOGLE_PAY_REQUEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_GOOGLE_PAY_REQUEST_WITH_EMAIL;

@ExtendWith(MockitoExtension.class)
 class WorldpayWalletAuthorisationHandlerTest {

    @Mock
    private GatewayClient mockGatewayClient;
    private GatewayAccountEntity gatewayAccountEntity;
    private Map<String, Object> gatewayAccountCredentials = Map.of(
            CREDENTIALS_MERCHANT_ID, "MERCHANTCODE",
            CREDENTIALS_USERNAME, "worldpay-password",
            CREDENTIALS_PASSWORD, "password"
    );
    private WorldpayWalletAuthorisationHandler worldpayWalletAuthorisationHandler;
    private ChargeEntity chargeEntity;

    @Captor
    ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor;

    @Captor
    ArgumentCaptor<Map<String, String>> headers;

    private static final URI WORLDPAY_URL = URI.create("http://worldpay.test");
    private static final String GOOGLE_PAY_3DS_WITHOUT_IP_ADDRESS = "uniqueSessionId";
    private static final String username = "worldpay-username";
    private static final String password = "password";

    @BeforeEach
     void setUp() throws Exception {
        worldpayWalletAuthorisationHandler = new WorldpayWalletAuthorisationHandler(mockGatewayClient, Map.of(TEST.toString(), WORLDPAY_URL));
        chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withDescription("This is the description")
                .withGatewayAccountCredentialsEntity(aGatewayAccountCredentialsEntity()
                        .withPaymentProvider("worldpay")
                        .withCredentials(Map.of(
                                CREDENTIALS_MERCHANT_ID, "MERCHANTCODE",
                                CREDENTIALS_USERNAME, username,
                                CREDENTIALS_PASSWORD, password
                        ))
                        .build())
                .build();
        gatewayAccountEntity = aGatewayAccountEntity()
                .withGatewayName("worldpay")
                .withType(TEST)
                .build();
        var creds = aGatewayAccountCredentialsEntity()
                .withCredentials(gatewayAccountCredentials)
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withPaymentProvider(WORLDPAY.getName())
                .withState(ACTIVE)
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(creds));
        chargeEntity.setGatewayTransactionId("MyUniqueTransactionId!");
        chargeEntity.setGatewayAccount(gatewayAccountEntity);
        when(mockGatewayClient.postRequestFor(any(URI.class), eq(WORLDPAY), eq("test"), any(GatewayOrder.class), anyMap()))
                .thenThrow(new GatewayErrorException("Unexpected HTTP status code 400 from gateway"));
    }

    @Test
     void shouldSendApplePayRequestWhenApplePayDetailsArePresent() throws Exception {
        try {
            worldpayWalletAuthorisationHandler.authorise(getApplePayAuthorisationRequest(false));
        } catch (GatewayErrorException e) {
            verify(mockGatewayClient).postRequestFor(eq(WORLDPAY_URL), eq(WORLDPAY), eq("test"), gatewayOrderArgumentCaptor.capture(), headers.capture());
            assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_APPLE_PAY_REQUEST),
                    gatewayOrderArgumentCaptor.getValue().getPayload());
            assertThat(headers.getValue().size(), is(1));
            String expectedHeader = "Basic " + Base64.getEncoder().encodeToString(new String( username + ":" + password).getBytes());
            assertThat(headers.getValue(), hasEntry(AUTHORIZATION, expectedHeader));
        }
    }

    @Test
     void shouldSendApplePayRequestWithPayerEmailWhenSendingPayerEmailToGatewayEnabledAndPayerEmailPresent() throws Exception {
        gatewayAccountEntity.setSendPayerEmailToGateway(true);
        try {
            worldpayWalletAuthorisationHandler.authorise(getApplePayAuthorisationRequest(true));
        } catch (GatewayErrorException e) {
            verify(mockGatewayClient).postRequestFor(eq(WORLDPAY_URL), eq(WORLDPAY), eq("test"), gatewayOrderArgumentCaptor.capture(), headers.capture());
            assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_APPLE_PAY_REQUEST_WITH_EMAIL),
                    gatewayOrderArgumentCaptor.getValue().getPayload());
            assertThat(headers.getValue().size(), is(1));
            String expectedHeader = "Basic " + Base64.getEncoder().encodeToString(new String( username + ":" + password).getBytes());
            assertThat(headers.getValue(), hasEntry(AUTHORIZATION, expectedHeader));
        }
    }

    @Test
     void shouldSendApplePayRequestWithoutPayerEmailWhenSendingPayerEmailToGatewayDisabledAndPayerEmailPresent() throws Exception {
        gatewayAccountEntity.setSendPayerEmailToGateway(false);
        try {
            worldpayWalletAuthorisationHandler.authorise(getApplePayAuthorisationRequest(true));
        } catch (GatewayErrorException e) {
            verify(mockGatewayClient).postRequestFor(eq(WORLDPAY_URL), eq(WORLDPAY), eq("test"), gatewayOrderArgumentCaptor.capture(), headers.capture());
            assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_APPLE_PAY_REQUEST),
                    gatewayOrderArgumentCaptor.getValue().getPayload());
            assertThat(headers.getValue().size(), is(1));
            String expectedHeader = "Basic " + Base64.getEncoder().encodeToString(new String( username + ":" + password).getBytes());
            assertThat(headers.getValue(), hasEntry(AUTHORIZATION, expectedHeader));
        }
    }

    @Test
     void shouldSendApplePayRequestWithoutPayerEmailWhenSendingPayerEmailToGatewayEnabledAndPayerEmailNotPresent() throws Exception {
        gatewayAccountEntity.setSendPayerEmailToGateway(true);
        try {
            worldpayWalletAuthorisationHandler.authorise(getApplePayAuthorisationRequest(false));
        } catch (GatewayErrorException e) {
            verify(mockGatewayClient).postRequestFor(eq(WORLDPAY_URL), eq(WORLDPAY), eq("test"), gatewayOrderArgumentCaptor.capture(), headers.capture());
            assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_APPLE_PAY_REQUEST),
                    gatewayOrderArgumentCaptor.getValue().getPayload());
            assertThat(headers.getValue().size(), is(1));
            String expectedHeader = "Basic " + Base64.getEncoder().encodeToString(new String( username + ":" + password).getBytes());
            assertThat(headers.getValue(), hasEntry(AUTHORIZATION, expectedHeader));
        }
    }

    @Test
     void shouldSendGooglePayRequestWhenGooglePayDetailsArePresent() throws Exception {
        try {
            worldpayWalletAuthorisationHandler.authorise(getGooglePayAuthorisationRequest(false));
        } catch (GatewayErrorException e) {
            verify(mockGatewayClient).postRequestFor(eq(WORLDPAY_URL), eq(WORLDPAY), eq("test"), gatewayOrderArgumentCaptor.capture(), headers.capture());
            assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_GOOGLE_PAY_REQUEST),
                    gatewayOrderArgumentCaptor.getValue().getPayload());
            assertThat(headers.getValue().size(), is(1));
            String expectedHeader = "Basic " + Base64.getEncoder().encodeToString(new String( username + ":" + password).getBytes());
            assertThat(headers.getValue(), hasEntry(AUTHORIZATION, expectedHeader));
        }
    }

    @Test
     void shouldSendGooglePayRequestWithEmailWhenSendingPayerEmailToGatewayEnabledAndPayerEmailPresent() throws Exception {
        gatewayAccountEntity.setSendPayerEmailToGateway(true);
        try {
            worldpayWalletAuthorisationHandler.authorise(getGooglePayAuthorisationRequest(true));
        } catch (GatewayErrorException e) {
            verify(mockGatewayClient).postRequestFor(eq(WORLDPAY_URL), eq(WORLDPAY), eq("test"), gatewayOrderArgumentCaptor.capture(), headers.capture());
            assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_GOOGLE_PAY_REQUEST_WITH_EMAIL),
                    gatewayOrderArgumentCaptor.getValue().getPayload());
            assertThat(headers.getValue().size(), is(1));
            String expectedHeader = "Basic " + Base64.getEncoder().encodeToString(new String( username + ":" + password).getBytes());
            assertThat(headers.getValue(), hasEntry(AUTHORIZATION, expectedHeader));
        }
    }

    @Test
     void shouldSendGooglePayRequestWithoutEmailWhenSendingPayerEmailToGatewayDisabledAndPayerEmailPresent() throws Exception {
        gatewayAccountEntity.setSendPayerEmailToGateway(false);
        try {
            worldpayWalletAuthorisationHandler.authorise(getGooglePayAuthorisationRequest(true));
        } catch (GatewayErrorException e) {
            verify(mockGatewayClient).postRequestFor(eq(WORLDPAY_URL), eq(WORLDPAY), eq("test"), gatewayOrderArgumentCaptor.capture(), headers.capture());
            assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_GOOGLE_PAY_REQUEST),
                    gatewayOrderArgumentCaptor.getValue().getPayload());
            assertThat(headers.getValue().size(), is(1));
            String expectedHeader = "Basic " + Base64.getEncoder().encodeToString(new String( username + ":" + password).getBytes());
            assertThat(headers.getValue(), hasEntry(AUTHORIZATION, expectedHeader));
        }
    }

    @Test
     void shouldSendGooglePayRequestWithoutEmailWhenSendingPayerEmailToGatewayEnabledAndPayerEmailNotPresent() throws Exception {
        gatewayAccountEntity.setSendPayerEmailToGateway(true);
        try {
            worldpayWalletAuthorisationHandler.authorise(getGooglePayAuthorisationRequest(false));
        } catch (GatewayErrorException e) {
            verify(mockGatewayClient).postRequestFor(eq(WORLDPAY_URL), eq(WORLDPAY), eq("test"), gatewayOrderArgumentCaptor.capture(), headers.capture());
            assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_GOOGLE_PAY_REQUEST),
                    gatewayOrderArgumentCaptor.getValue().getPayload());
            assertThat(headers.getValue().size(), is(1));
            String expectedHeader = "Basic " + Base64.getEncoder().encodeToString(new String( username + ":" + password).getBytes());
            assertThat(headers.getValue(), hasEntry(AUTHORIZATION, expectedHeader));
        }
    }

    @Test
     void shouldSendGooglePay3dsRequestWithDDCResultWhenWorldpay3dsFlexResultIsAvailable() throws Exception {
        try {
            worldpayWalletAuthorisationHandler.authorise(getGooglePay3dsAuthorisationRequest(true, true, false, true));
        } catch (GatewayErrorException e) {
            verify(mockGatewayClient).postRequestFor(eq(WORLDPAY_URL), eq(WORLDPAY), eq("test"), gatewayOrderArgumentCaptor.capture(), headers.capture());
            assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_AUTHORISE_GOOGLE_PAY_3DS_REQUEST_WITH_DDC_RESULT),
                    gatewayOrderArgumentCaptor.getValue().getPayload());
            assertThat(headers.getValue().size(), is(1));
            String expectedHeader = "Basic " + Base64.getEncoder().encodeToString(new String( username + ":" + password).getBytes());
            assertThat(headers.getValue(), hasEntry(AUTHORIZATION, expectedHeader));
        }
    }

    @Test
     void shouldSendGooglePay3dsRequestWhenGooglePayDetailsWithoutIpAddressArePresent() throws Exception {
        try {
            worldpayWalletAuthorisationHandler.authorise(getGooglePay3dsAuthorisationRequest(true, false, false, false));
        } catch (GatewayErrorException e) {
            verify(mockGatewayClient).postRequestFor(eq(WORLDPAY_URL), eq(WORLDPAY), eq("test"), gatewayOrderArgumentCaptor.capture(), headers.capture());
            assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_GOOGLE_PAY_3DS_REQUEST_WITHOUT_IP_ADDRESS),
                    gatewayOrderArgumentCaptor.getValue().getPayload());
            assertThat(headers.getValue().size(), is(1));
            String expectedHeader = "Basic " + Base64.getEncoder().encodeToString(new String( username + ":" + password).getBytes());
            assertThat(headers.getValue(), hasEntry(AUTHORIZATION, expectedHeader));
        }
    }

    @Test
     void shouldSendGooglePay3dsRequestWhenGooglePayDetailsWithIpAddressArePresent() throws Exception {
        try {
            worldpayWalletAuthorisationHandler.authorise(getGooglePay3dsAuthorisationRequest(true, true, false, false));
        } catch (GatewayErrorException e) {
            verify(mockGatewayClient).postRequestFor(eq(WORLDPAY_URL), eq(WORLDPAY), eq("test"), gatewayOrderArgumentCaptor.capture(), headers.capture());
            assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_GOOGLE_PAY_3DS_REQUEST_WITH_IP_ADDRESS), gatewayOrderArgumentCaptor.getValue().getPayload());
            assertThat(headers.getValue().size(), is(1));
            String expectedHeader = "Basic " + Base64.getEncoder().encodeToString(new String( username + ":" + password).getBytes());
            assertThat(headers.getValue(), hasEntry(AUTHORIZATION, expectedHeader));
        }
    }

    @Test
     void shouldSendGooglePay3dsRequestWhenGooglePayDetailsWithout3dsEnabledArePresent() throws Exception {
        try {
            worldpayWalletAuthorisationHandler.authorise(getGooglePay3dsAuthorisationRequest(false, true, false, false));
        } catch (GatewayErrorException e) {
            verify(mockGatewayClient).postRequestFor(eq(WORLDPAY_URL), eq(WORLDPAY), eq("test"), gatewayOrderArgumentCaptor.capture(), headers.capture());
            assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_GOOGLE_PAY_REQUEST), gatewayOrderArgumentCaptor.getValue().getPayload());
            assertThat(headers.getValue().size(), is(1));
            String expectedHeader = "Basic " + Base64.getEncoder().encodeToString(new String( username + ":" + password).getBytes());
            assertThat(headers.getValue(), hasEntry(AUTHORIZATION, expectedHeader));
        }
    }

    @Test
     void shouldSendGooglePay3dsRequestWithEmailWhenSendingPayerEmailToGatewayEnabledAndPayerEmailPresent() throws Exception {
        gatewayAccountEntity.setSendPayerEmailToGateway(true);
        try {
            worldpayWalletAuthorisationHandler.authorise(getGooglePay3dsAuthorisationRequest(true, false, true, false));
        } catch (GatewayErrorException e) {
            verify(mockGatewayClient).postRequestFor(eq(WORLDPAY_URL), eq(WORLDPAY), eq("test"), gatewayOrderArgumentCaptor.capture(), headers.capture());
            assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_GOOGLE_PAY_3DS_REQUEST_WITH_EMAIL),
                    gatewayOrderArgumentCaptor.getValue().getPayload());
            assertThat(headers.getValue().size(), is(1));
            String expectedHeader = "Basic " + Base64.getEncoder().encodeToString(new String( username + ":" + password).getBytes());
            assertThat(headers.getValue(), hasEntry(AUTHORIZATION, expectedHeader));
        }
    }

    @Test
     void shouldSendGooglePay3dsRequestWithEmailWhenSendingPayerEmailToGatewayDisabledAndPayerEmailPresent() throws Exception {
        gatewayAccountEntity.setSendPayerEmailToGateway(false);
        try {
            worldpayWalletAuthorisationHandler.authorise(getGooglePay3dsAuthorisationRequest(true, false, true, false));
        } catch (GatewayErrorException e) {
            verify(mockGatewayClient).postRequestFor(eq(WORLDPAY_URL), eq(WORLDPAY), eq("test"), gatewayOrderArgumentCaptor.capture(), headers.capture());
            assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_GOOGLE_PAY_3DS_REQUEST_WITHOUT_IP_ADDRESS),
                    gatewayOrderArgumentCaptor.getValue().getPayload());
            assertThat(headers.getValue().size(), is(1));
            String expectedHeader = "Basic " + Base64.getEncoder().encodeToString(new String( username + ":" + password).getBytes());
            assertThat(headers.getValue(), hasEntry(AUTHORIZATION, expectedHeader));
        }
    }

    @Test
     void shouldSendGooglePay3dsRequestWithEmailWhenSendingPayerEmailToGatewayEnabledAndPayerEmailNotPresent() throws Exception {
        gatewayAccountEntity.setSendPayerEmailToGateway(true);
        try {
            worldpayWalletAuthorisationHandler.authorise(getGooglePay3dsAuthorisationRequest(true, false, false, false));
        } catch (GatewayErrorException e) {
            verify(mockGatewayClient).postRequestFor(eq(WORLDPAY_URL), eq(WORLDPAY), eq("test"), gatewayOrderArgumentCaptor.capture(), headers.capture());
            assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_GOOGLE_PAY_3DS_REQUEST_WITHOUT_IP_ADDRESS),
                    gatewayOrderArgumentCaptor.getValue().getPayload());
            assertThat(headers.getValue().size(), is(1));
            String expectedHeader = "Basic " + Base64.getEncoder().encodeToString(new String( username + ":" + password).getBytes());
            assertThat(headers.getValue(), hasEntry(AUTHORIZATION, expectedHeader));
        }
    }

    private WalletAuthorisationGatewayRequest getGooglePayAuthorisationRequest(boolean withPayerEmail) throws IOException {
        String fixturePath = withPayerEmail ? "googlepay/example-auth-request.json" : "googlepay/example-auth-request-without-email.json";
        GooglePayAuthRequest googlePayAuthRequest = Jackson.getObjectMapper().readValue(fixture(fixturePath), GooglePayAuthRequest.class);
        return new WalletAuthorisationGatewayRequest(chargeEntity, googlePayAuthRequest);
    }

    private WalletAuthorisationGatewayRequest getGooglePay3dsAuthorisationRequest(boolean isRequires3ds, boolean withIpAddress, boolean withPayerEmail,
                                                                                  boolean withDDCResult)
            throws IOException {
        String fixturePath = withDDCResult ? "googlepay/example-3ds-auth-request-with-ddc.json" :
                             withPayerEmail ? "googlepay/example-3ds-auth-request.json" :
                             "googlepay/example-3ds-auth-request-without-email.json";
        GooglePayAuthRequest googlePayAuthRequest = Jackson.getObjectMapper().readValue(fixture(fixturePath), GooglePayAuthRequest.class);
        chargeEntity.getGatewayAccount().setRequires3ds(isRequires3ds);
        chargeEntity.getGatewayAccount().setSendPayerIpAddressToGateway(withIpAddress);
        chargeEntity.setExternalId(GOOGLE_PAY_3DS_WITHOUT_IP_ADDRESS);
        return new WalletAuthorisationGatewayRequest(chargeEntity, googlePayAuthRequest);
    }

    private WalletAuthorisationGatewayRequest getApplePayAuthorisationRequest(boolean withPayerEmail) {
        String payerEmail = withPayerEmail ? "aaa@bbb.test" : null;
        AppleDecryptedPaymentData data = new AppleDecryptedPaymentData(
                new WalletPaymentInfo(
                        "4242",
                        "visa",
                        PayersCardType.DEBIT,
                        "Mr. Payment",
                        payerEmail
                ),
                "4818528840010767",
                LocalDate.of(2023, 12, 1),
                "643",
                10L,
                "040010030273",
                "3DSecure",
                new AppleDecryptedPaymentData.PaymentData(
                        "Ao/fzpIAFvp1eB9y8WVDMAACAAA=",
                        "7"
                )
        );
        return new WalletAuthorisationGatewayRequest(chargeEntity, data);
    }

}
