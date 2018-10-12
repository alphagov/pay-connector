package uk.gov.pay.connector.util;

import com.jayway.restassured.response.ValidatableResponse;
import com.jayway.restassured.specification.RequestSpecification;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.http.ContentType.JSON;

public class RestAssuredClient {
    private final DropwizardAppWithPostgresRule app;
    private String accountId;
    private String chargeId;
    private String refundId;
    private Map<String, Object> queryParams;
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

    public RestAssuredClient withQueryParams(String paramName, List<String> paramValues) {
        this.queryParams.put(paramName, paramValues);
        return this;
    }

    public RestAssuredClient withHeader(String headerName, String headerValue) {
        this.headers.put(headerName, headerValue);
        return this;
    }

    //Need so was can set multiple values for a parameter e.g. foo=first&foo=second rather than foo=first,second
    private RequestSpecification addQueryParams(RequestSpecification requestSpecification) {
        RequestSpecification result = requestSpecification;
        for (Map.Entry<String, Object> queryParamPair : queryParams.entrySet()) {
            Object value = queryParamPair.getValue();
            if (value instanceof List) {
                for (Object collectionValue : (List)value) {
                    result.queryParam(queryParamPair.getKey(), collectionValue);
                }
            } else {
                result = result.queryParam(queryParamPair.getKey(), value);
            }
        }

        return result;
    }

    public ValidatableResponse postCreateCharge(String postBody) {
        String requestPath = "/v1/api/accounts/{accountId}/charges"
                .replace("{accountId}", accountId);

        return given().port(app.getLocalPort())
                .contentType(JSON)
                .body(postBody)
                .post(requestPath)
                .then();
    }

    public ValidatableResponse getCharge() {
        String requestPath = "/v1/api/accounts/{accountId}/charges/{chargeId}"
                .replace("{accountId}", accountId)
                .replace("{chargeId}", chargeId);

        return given().port(app.getLocalPort())
                .get(requestPath)
                .then();
    }

    public ValidatableResponse postChargeExpiryTask() {
        return given().port(app.getLocalPort())
                .post("/v1/tasks/expired-charges-sweep")
                .then();
    }

    public ValidatableResponse putChargeStatus(String putBody) {
        String requestPath = "/v1/frontend/charges/{chargeId}"
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
        String requestPath = "/v1/frontend/charges/{chargeId}"
                .replace("{accountId}", accountId)
                .replace("{chargeId}", chargeId);

        return given()
                .port(app.getLocalPort())
                .contentType(JSON).body(patchBody)
                .patch(requestPath)
                .then();
    }

    public ValidatableResponse postChargeCancellation() {
        String requestPath = "/v1/api/accounts/{accountId}/charges/{chargeId}"
                .replace("{accountId}", accountId)
                .replace("{chargeId}", chargeId)
                + "/cancel";
        return given().port(app.getLocalPort())
                .post(requestPath)
                .then();
    }

    public ValidatableResponse getTransactions() {
        return addQueryParams(given().port(app.getLocalPort())
                .headers(headers))
                .get("/v1/api/accounts/{accountId}/charges".replace("{accountId}", accountId))
                .then();
    }

    public ValidatableResponse getTransactionsV2() {
        return addQueryParams(given().port(app.getLocalPort())
                .headers(headers))
                .get("/v2/api/accounts/{accountId}/charges".replace("{accountId}", accountId))
                .then();
    }

    public ValidatableResponse getTransactionsAPI() {
        return addQueryParams(given().port(app.getLocalPort())
                .headers(headers))
                .get("/v1/api/accounts/{accountId}/transactions".replace("{accountId}", accountId))
                .then();
    }

    public ValidatableResponse getEvents(String chargeId) {
        String requestPath = "/v1/api/accounts/{accountId}/charges/{chargeId}/events"
                .replace("{accountId}", accountId)
                .replace("{chargeId}", chargeId);
        return given().port(app.getLocalPort())
                .get(requestPath)
                .then();
    }

    public ValidatableResponse getFrontendCharge() {
        String requestPath = "/v1/frontend/charges/{chargeId}"
                .replace("{chargeId}", chargeId);
        return given()
                .port(app.getLocalPort())
                .get(requestPath)
                .then();
    }

    public ValidatableResponse getRefund() {
        String requestPath = "/v1/api/accounts/{accountId}/charges/{chargeId}/refunds/{refundId}"
                .replace("{accountId}", accountId)
                .replace("{chargeId}", chargeId)
                .replace("{refundId}", refundId);
        return given()
                .port(app.getLocalPort())
                .get(requestPath)
                .then();
    }

    public ValidatableResponse getRefunds() {
        String requestPath = "/v1/api/accounts/{accountId}/refunds"
                .replace("{accountId}", accountId);
        return addQueryParams(given().port(app.getLocalPort()))
                .get(requestPath)
                .then();
    }

    public ValidatableResponse postFrontendChargeCancellation() {
        String requestPath = "/v1/frontend/charges/{chargeId}/cancel"
                .replace("{accountId}", accountId)
                .replace("{chargeId}", chargeId);
        return given().port(app.getLocalPort())
                .post(requestPath)
                .then();
    }

    public ValidatableResponse getCardTypes() {
        String requestPath = "/v1/api/card-types";
        return given().port(app.getLocalPort())
                .get(requestPath)
                .then();
    }

    public ValidatableResponse getTransactionsSummary() {
        String requestPath = "/v1/api/accounts/{accountId}/transactions-summary"
                .replace("{accountId}", accountId);

        return addQueryParams(given().port(app.getLocalPort()))
                .get(requestPath)
                .then();
    }

    public RestAssuredClient withRefundId(String refundId) {
        this.refundId = refundId;
        return this;
    }

    public ValidatableResponse postMarkChargeAsCaptureApproved() {
        final String path = "/v1/api/accounts/{accountId}/charges/{chargeId}/capture"
                .replace("{accountId}", accountId)
                .replace("{chargeId}", chargeId);
        return given().port(app.getLocalPort())
                .post(path)
                .then();
    }
}
