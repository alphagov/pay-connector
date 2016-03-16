package uk.gov.pay.connector.util;

import com.jayway.restassured.response.ValidatableResponse;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;

import java.util.HashMap;
import java.util.Map;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.http.ContentType.JSON;
import static uk.gov.pay.connector.resources.ApiPaths.*;

public class RestAssuredClient {
    private final DropwizardAppWithPostgresRule app;
    private String accountId;
    private String chargeId;
    private Map<String, String> queryParams;
    private Map<String, String> headers;

    public RestAssuredClient(DropwizardAppWithPostgresRule app, String accountId) {
        this.app = app;
        this.accountId = accountId;
        this.queryParams = new HashMap<>();
        this.headers = new HashMap<>();
    }

    public RestAssuredClient withAccountId(String accountId) {
        this.accountId = accountId;
        return this;
    }

    public RestAssuredClient withChargeId(String chargeId) {
        this.chargeId = chargeId;
        return this;
    }

    public RestAssuredClient withQueryParam(String paramName, String paramValue) {
        this.queryParams.put(paramName, paramValue);
        return this;
    }

    public RestAssuredClient withHeader(String headerName, String headerValue) {
        this.headers.put(headerName, headerValue);
        return this;
    }

    public ValidatableResponse postCreateCharge(String postBody) {
        String requestPath = CHARGES_API_PATH
                .replace("{accountId}", accountId);

        return given().port(app.getLocalPort())
                .contentType(JSON)
                .body(postBody)
                .post(requestPath)
                .then();
    }

    public ValidatableResponse postAdminTask(String postBody, String task) {
        String requestPath = "/tasks/"+task;

        return given().port(app.getAdminPort())
                .contentType(JSON)
                .body(postBody)
                .post(requestPath)
                .then();
    }

    public ValidatableResponse getCharge() {
        String requestPath = CHARGE_API_PATH
                .replace("{accountId}", accountId)
                .replace("{chargeId}", chargeId);

        return given().port(app.getLocalPort())
                .get(requestPath)
                .then();
    }

    public ValidatableResponse postChargeExpiryTask() {
        return given().port(app.getLocalPort())
                .post(EXPIRE_CHARGES)
                .then();
    }

    public ValidatableResponse putChargeStatus(String putBody) {
        String requestPath = CHARGE_FRONTEND_PATH
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
        String requestPath = CHARGE_API_PATH
                .replace("{accountId}", accountId)
                .replace("{chargeId}", chargeId)
                + "/cancel";
        return given().port(app.getLocalPort())
                .post(requestPath)
                .then();
    }

    public ValidatableResponse getTransactions() {
        return given().port(app.getLocalPort())
                .headers(headers)
                .queryParams(queryParams)
                .get(CHARGES_API_PATH.replace("{accountId}", accountId))
                .then();
    }

    public ValidatableResponse getEvents(Long chargeId) {
        String requestPath = CHARGE_EVENTS_API_PATH
                .replace("{accountId}", accountId)
                .replace("{chargeId}", String.valueOf(chargeId));
        return given().port(app.getLocalPort())
                .get(requestPath)
                .then();
    }

    public ValidatableResponse getFrontendCharge() {
        String requestPath = CHARGE_FRONTEND_PATH
                .replace("{chargeId}", chargeId);
        return given()
                .port(app.getLocalPort())
                .get(requestPath)
                .then();
    }
}
