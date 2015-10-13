package uk.gov.pay.connector.it.contract;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockserver.junit.MockServerRule;
import uk.gov.pay.connector.util.DatabaseTestHelper;
import uk.gov.pay.connector.util.DropwizardAppWithPostgresRule;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.http.ContentType.JSON;
import static io.dropwizard.testing.ConfigOverride.config;
import static org.eclipse.jetty.http.HttpStatus.OK_200;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.it.contract.SmartpayMockClient.CAPTURE_SUCCESS_PAYLOAD;
import static uk.gov.pay.connector.it.contract.SmartpayMockClient.UNKNOWN_STATUS_CODE;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.resources.CardResource.CAPTURE_FRONTEND_RESOURCE_PATH;
import static uk.gov.pay.connector.resources.PaymentProviderValidator.SMARTPAY_PROVIDER;

public class SmartpayStubInvalidUrlITest {
    private static final String ACCOUNT_ID = "12341234";
    private static final String CHARGE_ID = "111";
    private static final String TRANSACTION_ID = "7914440428682669";

    @Rule
    public MockServerRule smartpayMockRule = new MockServerRule(this);

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule(
            config("smartpay.url", "http://gobbledygook.invalid.url")
    );

    private SmartpayMockClient smartpayMock;
    private DatabaseTestHelper db;

    @Before
    public void setup() {
        smartpayMock = new SmartpayMockClient(smartpayMockRule.getHttpPort(), TRANSACTION_ID);
        db = app.getDatabaseTestHelper();
    }

    @Test
    public void failedCapture_InvalidConnectorUrl() throws Exception {
        setupForCapture();

        smartpayMock.respondWithStatusCodeAndPayloadWhenCapture(
                UNKNOWN_STATUS_CODE,
                CAPTURE_SUCCESS_PAYLOAD
        );

        String errorMessage = "Gateway Url DNS resolution error";
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

    private void setupForCapture() {
        db.addGatewayAccount(ACCOUNT_ID, SMARTPAY_PROVIDER);
        long amount = 3333;
        db.addCharge(CHARGE_ID, ACCOUNT_ID, amount, AUTHORISATION_SUCCESS, "return_url", TRANSACTION_ID);
    }
}
