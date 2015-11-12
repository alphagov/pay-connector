package uk.gov.pay.connector.util;

import com.jayway.restassured.response.ValidatableResponse;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.http.ContentType.JSON;

public class RestAssuredClient {
    private DropwizardAppWithPostgresRule app;
    private String accountId;
    private String requestPathTemplate;
    private String chargeId;
    private String requestPath;
    private boolean requestPathOverride;

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

    public RestAssuredClient withRequestPath(String requestPath) {
        this.requestPath = requestPath;
        requestPathOverride = true;
        return this;
    }

    public ValidatableResponse postCreateCharge(String postBody) {
        if (!requestPathOverride) {
            requestPath = requestPathTemplate
                    .replace("{accountId}", accountId);
        }

        return given().port(app.getLocalPort())
                .contentType(JSON)
                .body(postBody)
                .post(requestPath)
                .then();
    }

    public ValidatableResponse getCharge(String chargeId1) {
        if(requestPathOverride) {
            requestPath  = requestPath
                    .replace("{chargeId}", chargeId);
        } else {
            requestPath = requestPathTemplate
                    .replace("{accountId}", accountId)
                    .replace("{chargeId}", chargeId);
        }

        return given().port(app.getLocalPort())
                .get(requestPath)
                .then();
    }

    public ValidatableResponse putChargeStatus(String chargeId1, String putBody) {
        String requestPath = requestPathTemplate
                .replace("{accountId}", accountId)
                .replace("{chargeId}", chargeId)
                + "/status";
        System.out.println("requestPath = " + requestPath);
        return given()
                .port(app.getLocalPort())
                .contentType(JSON).body(putBody)
                .put(requestPath)
                .then();
    }

    public ValidatableResponse postChargeCancellation(String chargeId1) {
        String requestPath = requestPathTemplate
                .replace("{accountId}", accountId)
                .replace("{chargeId}", chargeId)
                + "/cancel";
        System.out.println("requestPath = " + requestPath);
        return given().port(app.getLocalPort())
                .post(requestPath)
                .then();
    }

    public ValidatableResponse getTransactions(String accountId1) {
        if(!requestPathOverride) {
            requestPath = requestPathTemplate;
        }
        return given().port(app.getLocalPort())
                .get(requestPath + "?gatewayAccountId=" + accountId)
                .then();
    }
}
