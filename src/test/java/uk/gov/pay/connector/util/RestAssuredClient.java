package uk.gov.pay.connector.util;

import com.jayway.restassured.response.ValidatableResponse;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.http.ContentType.JSON;

public class RestAssuredClient {
    private final DropwizardAppWithPostgresRule app;
    private final String requestPathTemplate;
    private String accountId;
    private String chargeId;

    public RestAssuredClient(DropwizardAppWithPostgresRule app, String accountId, String requestPathTemplate) {
        this.app = app;
        this.accountId = accountId;
        this.requestPathTemplate = requestPathTemplate;
    }

    public RestAssuredClient withAccountId(String accountId) {
        this.accountId = accountId;
        return this;
    }

    public RestAssuredClient withChargeId(String chargeId) {
        this.chargeId = chargeId;
        return this;
    }

    public ValidatableResponse postCreateCharge(String postBody) {
        String requestPath = requestPathTemplate
                .replace("{accountId}", accountId);

        return given().port(app.getLocalPort())
                .contentType(JSON)
                .body(postBody)
                .post(requestPath)
                .then();
    }

    public ValidatableResponse getCharge() {
        String requestPath = requestPathTemplate
                .replace("{accountId}", accountId)
                .replace("{chargeId}", chargeId);

        return given().port(app.getLocalPort())
                .get(requestPath)
                .then();
    }

    public ValidatableResponse putChargeStatus(String putBody) {
        String requestPath = requestPathTemplate
                .replace("{accountId}", accountId)
                .replace("{chargeId}", chargeId)
                + "/status";
        return given()
                .port(app.getLocalPort())
                .contentType(JSON).body(putBody)
                .put(requestPath)
                .then();
    }

    public ValidatableResponse postChargeCancellation() {
        String requestPath = requestPathTemplate
                .replace("{accountId}", accountId)
                .replace("{chargeId}", chargeId)
                + "/cancel";
        return given().port(app.getLocalPort())
                .post(requestPath)
                .then();
    }

    public ValidatableResponse getTransactions() {
        return given().port(app.getLocalPort())
                .get(requestPathTemplate + "?gatewayAccountId=" + accountId)
                .then();
    }
}
