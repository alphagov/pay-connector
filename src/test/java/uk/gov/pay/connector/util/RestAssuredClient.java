package uk.gov.pay.connector.util;

import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;

import javax.ws.rs.client.Entity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;

public class RestAssuredClient {
    private final int port;
    private String accountId;
    private String chargeId;
    private String refundId;
    private Map<String, Object> queryParams;
    private Map<String, String> headers;

    public RestAssuredClient(int port, String accountId) {
        this.port = port;
        this.accountId = accountId;
        this.queryParams = new HashMap<>();
        this.headers = new HashMap<>();
    }

    public RestAssuredClient(int port) {
        this(port, null);
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
                for (Object collectionValue : (List) value) {
                    result.queryParam(queryParamPair.getKey(), collectionValue);
                }
            } else {
                result = result.queryParam(queryParamPair.getKey(), value);
            }
        }

        return result;
    }

    public ValidatableResponse postCreateCharge(String postBody) {
        return postCreateCharge(postBody, accountId);
    }

    public ValidatableResponse postCreateCharge(String postBody, String accountId) {
        String requestPath = "/v1/api/accounts/{accountId}/charges"
                .replace("{accountId}", accountId);

        return buildRequest()
                .contentType(JSON)
                .body(postBody)
                .post(requestPath)
                .then();
    }

    public ValidatableResponse postCreateTelephoneCharge(String postBody) {
        String requestPath = "/v1/api/accounts/{accountId}/telephone-charges"
                .replace("{accountId}", accountId);

        return buildRequest()
                .contentType(JSON)
                .body(postBody)
                .post(requestPath)
                .then();
    }

    public ValidatableResponse getCharge() {
        String requestPath = "/v1/api/accounts/{accountId}/charges/{chargeId}"
                .replace("{accountId}", accountId)
                .replace("{chargeId}", chargeId);

        return buildRequest()
                .get(requestPath)
                .then();
    }

    public ValidatableResponse postChargeExpiryTask() {
        return buildRequest()
                .post("/v1/tasks/expired-charges-sweep")
                .then();
    }

    public ValidatableResponse postEmittedEventsSweepTask() {
        return buildRequest()
                .body(Entity.json(""))
                .post("/v1/tasks/emitted-events-sweep")
                .then();
    }

    public ValidatableResponse putChargeStatus(String putBody) {
        String requestPath = "/v1/frontend/charges/{chargeId}"
                .replace("{accountId}", accountId)
                .replace("{chargeId}", chargeId)
                + "/status";
        return buildRequest()
                .contentType(JSON).body(putBody)
                .put(requestPath)
                .then();
    }

    public ValidatableResponse patchCharge(String patchBody) {
        String requestPath = "/v1/frontend/charges/{chargeId}"
                .replace("{accountId}", accountId)
                .replace("{chargeId}", chargeId);

        return buildRequest()
                .contentType(JSON).body(patchBody)
                .patch(requestPath)
                .then();
    }

    public ValidatableResponse postChargeCancellation() {
        String requestPath = "/v1/api/accounts/{accountId}/charges/{chargeId}"
                .replace("{accountId}", accountId)
                .replace("{chargeId}", chargeId)
                + "/cancel";
        return buildRequest()
                .post(requestPath)
                .then();
    }

    public ValidatableResponse getEvents(String chargeId) {
        String requestPath = "/v1/api/accounts/{accountId}/charges/{chargeId}/events"
                .replace("{accountId}", accountId)
                .replace("{chargeId}", chargeId);
        return buildRequest()
                .get(requestPath)
                .then();
    }

    public ValidatableResponse getFrontendCharge() {
        String requestPath = "/v1/frontend/charges/{chargeId}"
                .replace("{chargeId}", chargeId);
        return buildRequest()
                .get(requestPath)
                .then();
    }

    public ValidatableResponse getWorldpay3dsFlexDdcJwt() {
        return buildRequest()
                .get("/v1/frontend/charges/{chargeId}/worldpay/3ds-flex/ddc"
                .replace("{chargeId}", chargeId))
                .then();
    }

    public ValidatableResponse getRefund() {
        String requestPath = "/v1/api/accounts/{accountId}/charges/{chargeId}/refunds/{refundId}"
                .replace("{accountId}", accountId)
                .replace("{chargeId}", chargeId)
                .replace("{refundId}", refundId);
        return buildRequest()
                .get(requestPath)
                .then();
    }

    public ValidatableResponse postFrontendChargeCancellation() {
        String requestPath = "/v1/frontend/charges/{chargeId}/cancel"
                .replace("{accountId}", accountId)
                .replace("{chargeId}", chargeId);
        return buildRequest()
                .post(requestPath)
                .then();
    }

    public RestAssuredClient withRefundId(String refundId) {
        this.refundId = refundId;
        return this;
    }

    public ValidatableResponse postMarkChargeAsCaptureApprovedByChargeIdAndAccountId() {
        final String path = "/v1/api/accounts/{accountId}/charges/{chargeId}/capture"
                .replace("{accountId}", accountId)
                .replace("{chargeId}", chargeId);
        return buildRequest()
                .post(path)
                .then();
    }

    public ValidatableResponse postMarkChargeAsCaptureApprovedByChargeId() {
        final String path = "/v1/api/charges/{chargeId}/capture"
                .replace("{chargeId}", chargeId);
        return buildRequest()
                .post(path)
                .then();
    }

    public ValidatableResponse getDiscrepancyReport(String chargeIds) {
        return addQueryParams(buildRequest())
                .contentType(JSON).body(chargeIds)
                .post("/v1/api/discrepancies/report")
                .then();
    }

    public ValidatableResponse resolveDiscrepancies(String chargeIds) {
        return addQueryParams(buildRequest())
                .contentType(JSON).body(chargeIds)
                .post("/v1/api/discrepancies/resolve")
                .then();
    }

    private RequestSpecification buildRequest() {
        return given()
                .port(port)
                .headers(headers);
    }
}
