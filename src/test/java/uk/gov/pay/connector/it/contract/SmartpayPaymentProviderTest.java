package uk.gov.pay.connector.it.contract;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import io.dropwizard.setup.Environment;
import org.hamcrest.CoreMatchers;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.GatewayConfig;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.handler.AuthorisationHandler;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayClientFactory;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.request.AuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.smartpay.SmartpayAuthorisationResponse;
import uk.gov.pay.connector.gateway.smartpay.SmartpayPaymentProvider;
import uk.gov.pay.connector.gateway.util.DefaultExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.gateway.worldpay.WorldpayCaptureResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.util.TestClientFactory;

import javax.ws.rs.client.Client;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.function.BiFunction;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.userExternalId;
import static uk.gov.pay.connector.util.AuthUtils.buildAuthCardDetails;
import static uk.gov.pay.connector.util.SystemUtils.envOrThrow;

@Ignore("Ignoring as this test is failing in Jenkins because it's failing to locate the certificates - PP-1707")
@RunWith(MockitoJUnitRunner.class)
public class SmartpayPaymentProviderTest {

    private String url = "https://pal-test.barclaycardsmartpay.com/pal/servlet/soap/Payment";
    private String username;
    private String password;
    private ChargeEntity chargeEntity;
    private GatewayAccountEntity gatewayAccountEntity;
    private MetricRegistry mockMetricRegistry;
    private DefaultExternalRefundAvailabilityCalculator defaultExternalRefundAvailabilityCalculator = new DefaultExternalRefundAvailabilityCalculator();


    @Before
    public void setUpAndCheckThatSmartpayIsUp() throws IOException {
        try {
            username = envOrThrow("GDS_CONNECTOR_SMARTPAY_USER");
            password = envOrThrow("GDS_CONNECTOR_SMARTPAY_PASSWORD");
        } catch (IllegalStateException ex) {
            Assume.assumeTrue("Ignoring test since credentials not configured", false);
        }

        new URL(url).openConnection().connect();
        Map<String, String> validSmartPayCredentials = ImmutableMap.of(
                "merchant_id", "DCOTest",
                "username", username,
                "password", password);
        gatewayAccountEntity = new GatewayAccountEntity();
        gatewayAccountEntity.setId(123L);
        gatewayAccountEntity.setGatewayName("smartpay");
        gatewayAccountEntity.setCredentials(validSmartPayCredentials);
        gatewayAccountEntity.setType(TEST);

        chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccountEntity).build();

        mockMetricRegistry = mock(MetricRegistry.class);
        Histogram mockHistogram = mock(Histogram.class);
        Counter mockCounter = mock(Counter.class);
        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);
        when(mockMetricRegistry.counter(anyString())).thenReturn(mockCounter);
    }

    @Test
    public void shouldSendSuccessfullyAnOrderForMerchant() throws Exception {
        SmartpayPaymentProvider paymentProvider = getSmartpayPaymentProvider();
        testCardAuthorisation(paymentProvider, chargeEntity);
    }

    @Test
    public void shouldSendA3dsOrderForMerchantSuccessfully() throws Exception {
        gatewayAccountEntity.setRequires3ds(true);
        SmartpayPaymentProvider paymentProvider = getSmartpayPaymentProvider();
        AuthorisationGatewayRequest request = getCard3dsAuthorisationRequest(chargeEntity);
        GatewayResponse<SmartpayAuthorisationResponse> response = paymentProvider.authorise(request);
        assertTrue(response.isSuccessful());
        assertThat(response.getBaseResponse().get().getIssuerUrl(), is(notNullValue()));
        assertThat(response.getBaseResponse().get().getMd(), is(notNullValue()));
        assertThat(response.getBaseResponse().get().getPaRequest(), is(notNullValue()));
    }

    @Test
    public void shouldFailRequestAuthorisationIfCredentialsAreNotCorrect() throws Exception {
        SmartpayPaymentProvider paymentProvider = getSmartpayPaymentProvider();
        GatewayAccountEntity accountWithInvalidCredentials = new GatewayAccountEntity();
        accountWithInvalidCredentials.setId(11L);
        accountWithInvalidCredentials.setGatewayName("smartpay");
        accountWithInvalidCredentials.setCredentials(ImmutableMap.of(
                "merchant_id", "MerchantAccount",
                "username", "wrong-username",
                "password", "wrong-password"
        ));
        accountWithInvalidCredentials.setType(TEST);

        chargeEntity.setGatewayAccount(accountWithInvalidCredentials);
        AuthorisationGatewayRequest request = getCardAuthorisationRequest(chargeEntity);
        GatewayResponse<SmartpayAuthorisationResponse> response = paymentProvider.authorise(request);

        assertFalse(response.isSuccessful());
        assertNotNull(response.getGatewayError());
    }

    @Test
    public void shouldSuccessfullySendACaptureRequest() throws Exception {
        SmartpayPaymentProvider paymentProvider = getSmartpayPaymentProvider();
        GatewayResponse<SmartpayAuthorisationResponse> response = testCardAuthorisation(paymentProvider, chargeEntity);

        assertThat(response.getBaseResponse().isPresent(), CoreMatchers.is(true));
        String transactionId = response.getBaseResponse().get().getPspReference();
        assertThat(transactionId, CoreMatchers.is(not(nullValue())));

        chargeEntity.setGatewayTransactionId(transactionId);

        GatewayResponse<WorldpayCaptureResponse> captureGatewayResponse = paymentProvider.capture(CaptureGatewayRequest.valueOf(chargeEntity));
        assertTrue(captureGatewayResponse.isSuccessful());
    }

    @Test
    public void shouldSuccessfullySendACancelRequest() throws Exception {
        SmartpayPaymentProvider paymentProvider = getSmartpayPaymentProvider();

        GatewayResponse<SmartpayAuthorisationResponse> response = testCardAuthorisation(paymentProvider, chargeEntity);

        assertThat(response.getBaseResponse().isPresent(), CoreMatchers.is(true));
        String transactionId = response.getBaseResponse().get().getPspReference();
        assertThat(transactionId, CoreMatchers.is(not(nullValue())));

        chargeEntity.setGatewayTransactionId(transactionId);

        GatewayResponse cancelResponse = paymentProvider.cancel(CancelGatewayRequest.valueOf(chargeEntity));
        assertThat(cancelResponse.isSuccessful(), is(true));

    }

    @Test
    public void shouldRefundToAnExistingPaymentSuccessfully() throws Exception {
        AuthorisationGatewayRequest request = getCardAuthorisationRequest(chargeEntity);
        SmartpayPaymentProvider paymentProvider = getSmartpayPaymentProvider();
        PaymentProvider smartpay = getSmartpayPaymentProvider();
        GatewayResponse<SmartpayAuthorisationResponse> authoriseResponse = paymentProvider.authorise(request);
        assertTrue(authoriseResponse.isSuccessful());

        chargeEntity.setGatewayTransactionId(authoriseResponse.getBaseResponse().get().getPspReference());

        GatewayResponse<WorldpayCaptureResponse> captureGatewayResponse = smartpay.capture(CaptureGatewayRequest.valueOf(chargeEntity));
        assertTrue(captureGatewayResponse.isSuccessful());

        RefundEntity refundEntity = new RefundEntity(chargeEntity, 1L, userExternalId);
        RefundGatewayRequest refundRequest = RefundGatewayRequest.valueOf(refundEntity);
        GatewayResponse refundResponse = smartpay.refund(refundRequest);

        assertThat(refundResponse.isSuccessful(), is(true));

    }

    private GatewayResponse testCardAuthorisation(AuthorisationHandler<SmartpayAuthorisationResponse> paymentProvider, ChargeEntity chargeEntity) {
        AuthorisationGatewayRequest request = getCardAuthorisationRequest(chargeEntity);
        GatewayResponse<SmartpayAuthorisationResponse> response = paymentProvider.authorise(request);
        assertTrue(response.isSuccessful());

        return response;
    }

    private SmartpayPaymentProvider getSmartpayPaymentProvider() {
        Client client = TestClientFactory.createJerseyClient();
        GatewayClient gatewayClient = new GatewayClient(client, ImmutableMap.of(TEST.toString(), url),
                SmartpayPaymentProvider.includeSessionIdentifier(), mockMetricRegistry);
        GatewayClientFactory gatewayClientFactory = mock(GatewayClientFactory.class);
        when(gatewayClientFactory.createGatewayClient(any(PaymentGatewayName.class), any(Map.class), any(BiFunction.class), null)).thenReturn(gatewayClient);
        ConnectorConfiguration configuration = mock(ConnectorConfiguration.class);
        GatewayConfig gatewayConfig = mock(GatewayConfig.class);
        when(configuration.getGatewayConfigFor(PaymentGatewayName.SMARTPAY)).thenReturn(gatewayConfig);
        return new SmartpayPaymentProvider(configuration, gatewayClientFactory, mock(Environment.class), new ObjectMapper());
    } 
    
    public static AuthorisationGatewayRequest getCardAuthorisationRequest(ChargeEntity chargeEntity) {
        Address address = new Address("41", "Scala Street", "EC2A 1AE", "London", "London", "GB");

        AuthCardDetails authCardDetails = aValidSmartpayCard();
        authCardDetails.setAddress(address);
        return new AuthorisationGatewayRequest(chargeEntity, authCardDetails);
    }

    public static AuthorisationGatewayRequest getCard3dsAuthorisationRequest(ChargeEntity chargeEntity) {
        Address address = new Address("6-60", "Simon Carmiggeltstraat", "1011DJ", "Amsterdam", "NH", "NL");

        AuthCardDetails authCardDetails = aValidSmartpay3dsCard();
        authCardDetails.setAddress(address);
        authCardDetails.setAcceptHeader("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        authCardDetails.setUserAgentHeader("Mozilla/5.0 (X11; U; Linux i686; en-US; rv:1.9) Gecko/2008052912 Firefox/3.0");

        return new AuthorisationGatewayRequest(chargeEntity, authCardDetails);
    }

    public static AuthCardDetails aValidSmartpayCard() {
        String validSandboxCard = "5555444433331111";
        return buildAuthCardDetails(validSandboxCard, "737", "08/18", "visa");
    }

    public static AuthCardDetails aValidSmartpay3dsCard() {
        String validSandboxCard = "5212345678901234";
        return buildAuthCardDetails(validSandboxCard, "737", "08/18", "master");
    }
}
