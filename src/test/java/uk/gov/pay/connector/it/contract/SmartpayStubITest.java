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
import uk.gov.pay.connector.util.DatabaseTestHelper;
import uk.gov.pay.connector.util.DropwizardAppWithPostgresRule;

import javax.ws.rs.client.ClientBuilder;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.http.ContentType.JSON;
import static io.dropwizard.testing.ConfigOverride.config;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.model.domain.GatewayAccount.gatewayAccountFor;
import static uk.gov.pay.connector.resources.CardResource.CAPTURE_FRONTEND_RESOURCE_PATH;
import static uk.gov.pay.connector.resources.PaymentProviderValidator.SMARTPAY_PROVIDER;

public class SmartpayStubITest {
    private static final String ACCOUNT_ID = "12341234";
    private static final String CHARGE_ID = "111";

    @Rule
    public MockServerRule smartpayMockRule = new MockServerRule(this);

    private SmartpayMockClient smartpayMock;
    private GatewayCredentialsConfig config;

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule(
            config("smartpay.url", "http://localhost:" + smartpayMockRule.getHttpPort())
    );
    private DatabaseTestHelper db;
    public static final String TRANSACTION_ID = "7914440428682669";

    @Before
    public void setup() {
        smartpayMock = new SmartpayMockClient(smartpayMockRule.getHttpPort());
        config = app.getConf().getSmartpayConfig();
        db = app.getDatabaseTestHelper();
    }

    @Test
    public void failedCapture_UnexpectedResponseCodeFromGateway() throws Exception {
        setupForCapture();

        smartpayMock.respondWithUnknownStatusCode_WhenCapture(TRANSACTION_ID);

        String errorMessage = "Unexpected Response Code From Gateway";
        String captureUrl = CAPTURE_FRONTEND_RESOURCE_PATH.replace("{chargeId}", CHARGE_ID);

        given()
                .port(app.getLocalPort())
                .contentType(JSON)
                .when()
                .post(captureUrl)
                .then()
                .statusCode(500)
                .contentType(JSON)
                .body("message", is(errorMessage));
        ;

        assertThat(db.getChargeStatus(CHARGE_ID), is(CAPTURE_UNKNOWN.getValue()));
    }

    @Test
    public void failedCapture_MalformedResponseFromGateway() throws Exception {
        setupForCapture();

        smartpayMock.respondWithMalformedBody_WhenCapture(TRANSACTION_ID);

        String errorMessage = "Invalid Response Received From Gateway";
        String captureUrl = CAPTURE_FRONTEND_RESOURCE_PATH.replace("{chargeId}", CHARGE_ID);

        given()
                .port(app.getLocalPort())
                .contentType(JSON)
                .when()
                .post(captureUrl)
                .then()
                .statusCode(500)
                .contentType(JSON)
                .body("message", is(errorMessage));
        ;

        assertThat(db.getChargeStatus(CHARGE_ID), is(CAPTURE_UNKNOWN.getValue()));
    }

    @Test
    public void successCapture_ResourceTest() throws Exception {
        setupForCapture();

        smartpayMock.respondSuccess_WhenCapture(TRANSACTION_ID);

        String captureUrl = CAPTURE_FRONTEND_RESOURCE_PATH.replace("{chargeId}", CHARGE_ID);

        given()
                .port(app.getLocalPort())
                .contentType(JSON)
                .when()
                .post(captureUrl)
                .then()
                .statusCode(204)
        ;

        assertThat(db.getChargeStatus(CHARGE_ID), is(CAPTURE_SUBMITTED.getValue()));
    }

    @Test
    public void smartpaySucessfulCapture() throws Exception {
        String amount = "5000000";

        smartpayMock.respondSuccess_WhenCapture(TRANSACTION_ID);

        PaymentProvider paymentProvider = getSmartpayPaymentProvider(config.getUsername(), config.getPassword());

        CaptureRequest captureRequest = new CaptureRequest(amount, TRANSACTION_ID);
        CaptureResponse captureResponse = paymentProvider.capture(captureRequest);
        assertTrue(captureResponse.isSuccessful());
        assertNull(captureResponse.getError());
    }

    private void setupForCapture() {
        db.addGatewayAccount(ACCOUNT_ID, SMARTPAY_PROVIDER);
        long amount = 3333;
        db.addCharge(CHARGE_ID, ACCOUNT_ID, amount, AUTHORISATION_SUCCESS, "return_url", TRANSACTION_ID);
    }

    private PaymentProvider getSmartpayPaymentProvider(String username, String password) throws Exception {
        String smartpayUrl = config.getUrl();
        GatewayClient gatewayClient = new GatewayClient(ClientBuilder.newClient(), smartpayUrl);
        GatewayAccount gatewayAccount = gatewayAccountFor(username, password);
        return new SmartpayPaymentProvider(gatewayClient, gatewayAccount);
    }
}
