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
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_ERROR;
import static uk.gov.pay.connector.resources.ApiPaths.FRONTEND_CHARGE_CAPTURE_API_PATH;

public class GatewayInvalidUrlITest {
    private static final String ACCOUNT_ID = "12341234";
    private static final Long CHARGE_ID = 111L;
    private static final String EXTERNAL_CHARGE_ID = "abcd";
    private static final String TRANSACTION_ID = "7914440428682669";
    private Map<String, String> defaultCredentials = ImmutableMap.of("username","a-user","password","a-password","merchant_id","aMerchantCode");

    private int port = PortFactory.findFreePort();

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(port);

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule(
            config("smartpay.urls.test", "http://gobbledygook.invalid.url")
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

        assertThat(db.getChargeStatus(CHARGE_ID), is(CAPTURE_APPROVED.getValue()));
    }

    private void setupForCapture() {
        db.addGatewayAccount(ACCOUNT_ID, "smartpay", defaultCredentials, "aGovService", GatewayAccountEntity.Type.TEST,"some description", "12345");
        long amount = 3333;
        db.addCharge(CHARGE_ID, EXTERNAL_CHARGE_ID, ACCOUNT_ID, amount, AUTHORISATION_SUCCESS, "return_url", TRANSACTION_ID);
    }
}
