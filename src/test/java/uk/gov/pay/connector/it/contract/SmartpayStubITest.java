package uk.gov.pay.connector.it.contract;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockserver.junit.MockServerRule;
import uk.gov.pay.connector.app.GatewayCredentialsConfig;
import uk.gov.pay.connector.model.CaptureRequest;
import uk.gov.pay.connector.model.CaptureResponse;
import uk.gov.pay.connector.model.domain.GatewayAccount;
import uk.gov.pay.connector.service.GatewayClient;
import uk.gov.pay.connector.service.PaymentProvider;
import uk.gov.pay.connector.service.smartpay.SmartpayPaymentProvider;
import uk.gov.pay.connector.util.DropwizardAppWithPostgresRule;

import javax.ws.rs.client.ClientBuilder;

import static io.dropwizard.testing.ConfigOverride.config;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static uk.gov.pay.connector.model.domain.GatewayAccount.gatewayAccountFor;

public class SmartpayStubITest {
    @Rule
    public MockServerRule smartpayMockRule = new MockServerRule(this);

    private SmartpayMockClient smartpayMock;
    private GatewayCredentialsConfig config;

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule(
            config("smartpay.url", smartpayUrl())
    );

    private String smartpayUrl() {
        return "http://localhost:" + smartpayMockRule.getHttpPort();
    }

    @Before
    public void setup() {
        smartpayMock = new SmartpayMockClient(smartpayMockRule.getHttpPort());
        config = app.getConf().getSmartpayConfig();
    }

    @Test
    public void smartpaySucessfulCapture() throws Exception {
        String transactionId = "7914440428682669";
        String amount = "5000000";

        smartpayMock.respondSuccess_WhenCapture(transactionId);

        PaymentProvider paymentProvider = getSmartpayPaymentProvider(config.getUsername(), config.getPassword());

        CaptureRequest captureRequest = new CaptureRequest(amount, transactionId);
        CaptureResponse captureResponse = paymentProvider.capture(captureRequest);
        assertTrue(captureResponse.isSuccessful());
        assertNull(captureResponse.getError());
    }

    private PaymentProvider getSmartpayPaymentProvider(String username, String password) throws Exception {
        String smartpayUrl = config.getUrl();
        GatewayClient gatewayClient = new GatewayClient(ClientBuilder.newClient(), smartpayUrl);
        GatewayAccount gatewayAccount = gatewayAccountFor(username, password);
        return new SmartpayPaymentProvider(gatewayClient, gatewayAccount);
    }
}
