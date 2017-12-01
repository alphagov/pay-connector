package uk.gov.pay.connector.util;

import com.jayway.restassured.response.ValidatableResponse;
import uk.gov.pay.connector.rules.AppWithPostgresRule;

import java.util.HashMap;
import java.util.Map;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.http.ContentType.JSON;
import static uk.gov.pay.connector.resources.ApiPaths.CARD_TYPES_API_PATH;
import static uk.gov.pay.connector.resources.ApiPaths.CHARGES_API_PATH;
import static uk.gov.pay.connector.resources.ApiPaths.CHARGES_EXPIRE_CHARGES_TASK_API_PATH;
import static uk.gov.pay.connector.resources.ApiPaths.CHARGE_API_PATH;
import static uk.gov.pay.connector.resources.ApiPaths.CHARGE_EVENTS_API_PATH;
import static uk.gov.pay.connector.resources.ApiPaths.FRONTEND_CHARGE_API_PATH;
import static uk.gov.pay.connector.resources.ApiPaths.FRONTEND_CHARGE_CANCEL_API_PATH;
import static uk.gov.pay.connector.resources.ApiPaths.REFUND_API_PATH;
import static uk.gov.pay.connector.resources.ApiPaths.TRANSACTIONS_SUMMARY_API_PATH;

public class RestAssuredClient {
    private final AppWithPostgresRule app;
    private String accountId;
    private String chargeId;
    private String refundId;
    private Map<String, String> queryParams;
    private Map<String, String> headers;

    public RestAssuredClient(AppWithPostgresRule app, String accountId) {
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
                .post(CHARGES_EXPIRE_CHARGES_TASK_API_PATH)
                .then();
    }

    public ValidatableResponse putChargeStatus(String putBody) {
        String requestPath = FRONTEND_CHARGE_API_PATH
                .replace("{accountId}", accountId)
                .replace("{chargeId}", chargeId)
                + "/status";
        return given()
                .port(app.getLocalPort())
                .contentType(JSON).body(putBody)
                .put(requestPath)
                .then();
    }

    public ValidatableResponse patchCharge(String patchBody) {
        String requestPath = FRONTEND_CHARGE_API_PATH
                .replace("{accountId}", accountId)
                .replace("{chargeId}", chargeId);

        return given()
                .port(app.getLocalPort())
                .contentType(JSON).body(patchBody)
                .patch(requestPath)
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

    public ValidatableResponse getEvents(String chargeId) {
        String requestPath = CHARGE_EVENTS_API_PATH
                .replace("{accountId}", accountId)
                .replace("{chargeId}", chargeId);
        return given().port(app.getLocalPort())
                .get(requestPath)
                .then();
    }

    public ValidatableResponse getFrontendCharge() {
        String requestPath = FRONTEND_CHARGE_API_PATH
                .replace("{chargeId}", chargeId);
        return given()
                .port(app.getLocalPort())
                .get(requestPath)
                .then();
    }

    public ValidatableResponse getRefund() {
        String requestPath = REFUND_API_PATH
                .replace("{accountId}", accountId)
                .replace("{chargeId}", chargeId)
                .replace("{refundId}", refundId);
        return given()
                .port(app.getLocalPort())
                .get(requestPath)
                .then();
    }

    public ValidatableResponse postFrontendChargeCancellation() {
        String requestPath = FRONTEND_CHARGE_CANCEL_API_PATH
                .replace("{accountId}", accountId)
                .replace("{chargeId}", chargeId);
        return given().port(app.getLocalPort())
                .post(requestPath)
                .then();
    }

    public ValidatableResponse getCardTypes() {
        String requestPath = CARD_TYPES_API_PATH;
        return given().port(app.getLocalPort())
                .get(requestPath)
                .then();
    }

    public ValidatableResponse getTransactionsSummary() {
        String requestPath = TRANSACTIONS_SUMMARY_API_PATH
                .replace("{accountId}", accountId);

        return given().port(app.getLocalPort())
                .queryParams(queryParams)
                .get(requestPath)
                .then();
    }

    public RestAssuredClient withRefundId(String refundId) {
        this.refundId = refundId;
        return this;
    }
}
