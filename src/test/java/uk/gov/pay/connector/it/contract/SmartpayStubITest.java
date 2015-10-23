package uk.gov.pay.connector.it.contract;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.RequestListener;
import com.github.tomakehurst.wiremock.http.Response;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.jayway.restassured.response.ValidatableResponse;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.util.DatabaseTestHelper;
import uk.gov.pay.connector.util.PortFactory;

import java.util.ArrayList;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.http.ContentType.JSON;
import static io.dropwizard.testing.ConfigOverride.config;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.resources.CardResource.CAPTURE_FRONTEND_RESOURCE_PATH;
import static uk.gov.pay.connector.resources.PaymentProviderValidator.SMARTPAY_PROVIDER;

public class SmartpayStubITest {
    private static final String ACCOUNT_ID = "12341234";
    private static final String CHARGE_ID = "111";
    private static final String TRANSACTION_ID = "7914440428682669";

    private int port = PortFactory.findFreePort();

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(port);

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule(
            config("smartpay.url", "http://localhost:" + port + "/pal/servlet/soap/Payment")
    );

    private SmartpayMockClient smartpayMock;
    private DatabaseTestHelper db;

    @Before
    public void setup() {
        smartpayMock = new SmartpayMockClient(TRANSACTION_ID);
        db = app.getDatabaseTestHelper();
    }

    @Test
    public void failedCapture_UnexpectedResponseCodeFromGateway() throws Exception {
        setupForCapture();

        smartpayMock.respondWithUnexpectedResponseCodeWhenCapture();

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
    @Ignore
    public void failedCapture_ConnectionTimeoutFromGateway() throws Exception {
        setupForCapture();

        WireMock.addRequestProcessingDelay(3000);
        smartpayMock.respondWithSuccessWhenCapture();
  //      WireMock.shutdownServer();
  //      WireMock.shutdownServer();
//        wireMockRule.shutdown();

        String errorMessage = "Connection Timeout From Gateway";
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

        smartpayMock.respondWithMalformedBody_WhenCapture();

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

        smartpayMock.respondWithSuccessWhenCapture();

        List<Request> requests = new ArrayList<>();
        wireMockRule.addMockServiceRequestListener(
                (request, response) ->
                        requests.add(LoggedRequest.createFrom(request))
        );

        String captureUrl = CAPTURE_FRONTEND_RESOURCE_PATH.replace("{chargeId}", CHARGE_ID);
        ValidatableResponse response = given()
                .port(app.getLocalPort())
                .contentType(JSON)
                .when()
                .post(captureUrl)
                .then()
                .log()
                .body();

        for (Request request : requests) {
            String url = request.getUrl();
            System.out.println("url = " + url);
            String body = request.getBodyAsString();
            System.out.println("body = " + body);
        }

        response
                .statusCode(204)
        ;

        assertThat(db.getChargeStatus(CHARGE_ID), is(CAPTURE_SUBMITTED.getValue()));
    }

    private void setupForCapture() {
        db.addGatewayAccount(ACCOUNT_ID, SMARTPAY_PROVIDER);
        long amount = 3333;
        db.addCharge(CHARGE_ID, ACCOUNT_ID, amount, AUTHORISATION_SUCCESS, "return_url", TRANSACTION_ID);
    }
}
