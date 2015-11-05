package uk.gov.pay.connector.it.gatewayclient;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.resources.CardResource;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.util.DatabaseTestHelper;
import uk.gov.pay.connector.util.PortFactory;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.http.ContentType.JSON;
import static io.dropwizard.testing.ConfigOverride.config;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.resources.CardResource.CAPTURE_FRONTEND_RESOURCE_PATH;
import static uk.gov.pay.connector.resources.PaymentProviderValidator.SMARTPAY_PROVIDER;
import static uk.gov.pay.connector.util.CardUtils.aValidCard;

public class GatewayFailuresITest {
    private static final String ACCOUNT_ID = "12341234";
    private static final String CHARGE_ID = "111";
    private static final String TRANSACTION_ID = "7914440428682669";
    private static final long AMOUNT = 3333;

    private int port = PortFactory.findFreePort();

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(port);

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule(
            config("smartpay.url", "http://localhost:" + port + "/pal/servlet/soap/Payment")
    );

    private GatewayStub gatewayStub;
    private DatabaseTestHelper db;

    @Before
    public void setup() {
        gatewayStub = new GatewayStub(TRANSACTION_ID);
        db = app.getDatabaseTestHelper();

        db.addGatewayAccount(ACCOUNT_ID, SMARTPAY_PROVIDER);
    }

    @Test
    public void failedAuth_UnexpectedResponseCodeFromGateway() throws Exception {
        setupForCardAuth();

        gatewayStub.respondWithUnexpectedResponseCodeWhenCardAuth();

        String errorMessage = "Unexpected Response Code From Gateway";
        String cardAuthUrl = CardResource.AUTHORIZATION_FRONTEND_RESOURCE_PATH.replace("{chargeId}", CHARGE_ID);

        given()
                .port(app.getLocalPort())
                .contentType(JSON)
                .when()
                .body(aValidCard())
                .post(cardAuthUrl)
                .then()
                .statusCode(500)
                .contentType(JSON)
                .body("message", is(errorMessage));

        assertThat(db.getChargeStatus(CHARGE_ID), is(ENTERING_CARD_DETAILS.getValue()));
    }

    @Test
    public void failedCapture_UnexpectedResponseCodeFromGateway() throws Exception {
        setupForCapture();
        gatewayStub.respondWithUnexpectedResponseCodeWhenCapture();

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

        assertThat(db.getChargeStatus(CHARGE_ID), is(CAPTURE_UNKNOWN.getValue()));
    }

    @Test
    public void failedCapture_MalformedResponseFromGateway() throws Exception {
        setupForCapture();

        gatewayStub.respondWithMalformedBody_WhenCapture();

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

        assertThat(db.getChargeStatus(CHARGE_ID), is(CAPTURE_UNKNOWN.getValue()));
    }

    @Test
    public void successCapture_ResourceTest() throws Exception {
        setupForCapture();

        gatewayStub.respondWithSuccessWhenCapture();

        String captureUrl = CAPTURE_FRONTEND_RESOURCE_PATH.replace("{chargeId}", CHARGE_ID);
        given()
                .port(app.getLocalPort())
                .contentType(JSON)
                .when()
                .post(captureUrl)
                .then()
                .statusCode(204);

        assertThat(db.getChargeStatus(CHARGE_ID), is(CAPTURE_SUBMITTED.getValue()));
    }

    private void setupForCapture() {
        db.addCharge(CHARGE_ID, ACCOUNT_ID, AMOUNT, AUTHORISATION_SUCCESS, "return_url", TRANSACTION_ID);
    }

    private void setupForCardAuth() {
        db.addCharge(CHARGE_ID, ACCOUNT_ID, AMOUNT, ENTERING_CARD_DETAILS, "return_url", TRANSACTION_ID);
    }
}
