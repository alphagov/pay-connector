package uk.gov.pay.connector.it.gatewayclient;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.util.DatabaseTestHelper;
import uk.gov.pay.connector.util.PortFactory;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.http.ContentType.JSON;
import static io.dropwizard.testing.ConfigOverride.config;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_UNKNOWN;
import static uk.gov.pay.connector.resources.CardResource.FRONTEND_CAPTURE_RESOURCE;
import static uk.gov.pay.connector.resources.PaymentProviderValidator.SMARTPAY_PROVIDER;

public class GatewayInvalidUrlITest {
    private static final String ACCOUNT_ID = "12341234";
    private static final String CHARGE_ID = "111";
    private static final String TRANSACTION_ID = "7914440428682669";

    private int port = PortFactory.findFreePort();

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(port);

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule(
            config("smartpay.url", "http://gobbledygook.invalid.url")
    );

    private GatewayStub gatewayStub;
    private DatabaseTestHelper db;

    @Before
    public void setup() {
        gatewayStub = new GatewayStub(TRANSACTION_ID);
        db = app.getDatabaseTestHelper();
    }

    @Test
    public void failedCapture_InvalidConnectorUrl() throws Exception {
        setupForCapture();

        gatewayStub.respondWithUnexpectedResponseCodeWhenCapture();

        String errorMessage = "Gateway Url DNS resolution error";
        String captureUrl = FRONTEND_CAPTURE_RESOURCE.replace("{chargeId}", CHARGE_ID);

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

    private void setupForCapture() {
        db.addGatewayAccount(ACCOUNT_ID, SMARTPAY_PROVIDER);
        long amount = 3333;
        db.addCharge(CHARGE_ID, ACCOUNT_ID, amount, AUTHORISATION_SUCCESS, "return_url", TRANSACTION_ID);
    }
}
