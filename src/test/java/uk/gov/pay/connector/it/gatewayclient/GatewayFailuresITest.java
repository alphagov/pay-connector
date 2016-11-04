package uk.gov.pay.connector.it.gatewayclient;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.util.DatabaseTestHelper;
import uk.gov.pay.connector.util.PortFactory;

import java.util.Map;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.http.ContentType.JSON;
import static io.dropwizard.testing.ConfigOverride.config;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.resources.ApiPaths.FRONTEND_CHARGE_AUTHORIZE_API_PATH;
import static uk.gov.pay.connector.resources.ApiPaths.FRONTEND_CHARGE_CAPTURE_API_PATH;
import static uk.gov.pay.connector.util.CardUtils.aValidCard;

public class GatewayFailuresITest {
    private static final String ACCOUNT_ID = "12341234";
    private static final Long CHARGE_ID = 111L;
    private static final String EXTERNAL_CHARGE_ID = "abcd1234";
    private static final String TRANSACTION_ID = "7914440428682669";
    private static final long AMOUNT = 3333;
    private Map<String, String> defaultCredentials = ImmutableMap.of("username","a-user","password","a-password","merchant_id","aMerchantCode");

    private int port = PortFactory.findFreePort();

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(port);

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule(
            config("smartpay.urls.test", "http://localhost:" + port + "/pal/servlet/soap/Payment")
    );

    private GatewayStub gatewayStub;
    private DatabaseTestHelper db;

    @Before
    public void setup() {
        gatewayStub = new GatewayStub(TRANSACTION_ID);
        db = app.getDatabaseTestHelper();
        db.addGatewayAccount(ACCOUNT_ID, "smartpay", defaultCredentials, "aGovService", GatewayAccountEntity.Type.TEST,"some description", "12345");
    }

    @Test
    public void failedAuth_UnexpectedResponseCodeFromGateway() throws Exception {
        setupForCardAuth();

        gatewayStub.respondWithUnexpectedResponseCodeWhenCardAuth();

        String errorMessage = "Unexpected Response Code From Gateway";
        String cardAuthUrl = FRONTEND_CHARGE_AUTHORIZE_API_PATH.replace("{chargeId}", EXTERNAL_CHARGE_ID);

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

        assertThat(db.getChargeStatus(CHARGE_ID), is(AUTHORISATION_ERROR.getValue()));
    }

    @Test
    public void failedCapture_UnexpectedResponseCodeFromGateway() throws Exception {
        setupForCapture();
        gatewayStub.respondWithUnexpectedResponseCodeWhenCapture();

        String errorMessage = "Unexpected Response Code From Gateway";
        String captureUrl = FRONTEND_CHARGE_CAPTURE_API_PATH.replace("{chargeId}", EXTERNAL_CHARGE_ID);

        given()
                .port(app.getLocalPort())
                .contentType(JSON)
                .when()
                .post(captureUrl)
                .then()
                .statusCode(500)
                .contentType(JSON)
                .body("message", is(errorMessage));

        assertThat(db.getChargeStatus(CHARGE_ID), is(CAPTURE_ERROR.getValue()));
    }

    @Test
    public void failedCapture_MalformedResponseFromGateway() throws Exception {
        setupForCapture();

        gatewayStub.respondWithMalformedBody_WhenCapture();

        String errorMessage = "Invalid Response Received From Gateway";
        String captureUrl = FRONTEND_CHARGE_CAPTURE_API_PATH.replace("{chargeId}", EXTERNAL_CHARGE_ID);

        given()
                .port(app.getLocalPort())
                .contentType(JSON)
                .when()
                .post(captureUrl)
                .then()
                .statusCode(500)
                .contentType(JSON)
                .body("message", is(errorMessage));

        assertThat(db.getChargeStatus(CHARGE_ID), is(CAPTURE_ERROR.getValue()));
    }

    @Test
    public void successCapture_ResourceTest() throws Exception {
        setupForCapture();

        gatewayStub.respondWithSuccessWhenCapture();

        String captureUrl = FRONTEND_CHARGE_CAPTURE_API_PATH.replace("{chargeId}", EXTERNAL_CHARGE_ID);
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
        db.addCharge(CHARGE_ID, EXTERNAL_CHARGE_ID, ACCOUNT_ID, AMOUNT, AUTHORISATION_SUCCESS, "return_url", TRANSACTION_ID);
    }

    private void setupForCardAuth() {
        db.addCharge(CHARGE_ID, EXTERNAL_CHARGE_ID, ACCOUNT_ID, AMOUNT, ENTERING_CARD_DETAILS, "return_url", TRANSACTION_ID);
    }
}
