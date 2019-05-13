package uk.gov.pay.connector.rules;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.google.common.collect.ImmutableMap;
import org.json.JSONObject;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import java.util.Map;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_AUTHORISATION_FAILED_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_AUTHORISATION_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_CAPTURE_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_CREATE_3DS_SOURCES_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_CREATE_SOURCES_3DS_REQUIRED_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_CREATE_SOURCES_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_CREATE_TOKEN_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_ERROR_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_REFUND_FULL_CHARGE_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_TRANSFER_RESPONSE;

public class StripeMockClient {
    public void mockCreateToken() {
        String payload = TestTemplateResourceLoader.load(STRIPE_CREATE_TOKEN_SUCCESS_RESPONSE);
        setupResponse(payload, "/v1/tokens", 200);
    }

    private void setupResponse(String responseBody, String path, int status) {
        stubFor(post(urlPathEqualTo(path)).withHeader(CONTENT_TYPE, matching(APPLICATION_FORM_URLENCODED))
                .willReturn(aResponse().withHeader(CONTENT_TYPE, APPLICATION_JSON).withStatus(status).withBody(responseBody)));
    }

    private void setupResponse(String requestBodyPattern, String responseBody, String path, int status) {
        stubFor(post(urlPathEqualTo(path))
                .withHeader(CONTENT_TYPE, matching(APPLICATION_FORM_URLENCODED))
                .withRequestBody(matching(".*" + requestBodyPattern + ".*"))
                .willReturn(aResponse().withHeader(CONTENT_TYPE, APPLICATION_JSON).withStatus(status).withBody(responseBody)));
    }

    public void mockCreateCharge() {
        String payload = TestTemplateResourceLoader.load(STRIPE_AUTHORISATION_SUCCESS_RESPONSE);
        setupResponse(payload, "/v1/charges", 200);
    }

    public void mockCaptureSuccess(String gatewayTransactionId) {
        String payload = TestTemplateResourceLoader.load(STRIPE_CAPTURE_SUCCESS_RESPONSE);
        setupResponse(payload, "/v1/charges/" + gatewayTransactionId + "/capture", 200);
    }

    public void mockCaptureError(String gatewayTransactionId) {
        String payload = TestTemplateResourceLoader.load(STRIPE_ERROR_RESPONSE);
        setupResponse(payload, "/v1/charges/" + gatewayTransactionId + "/capture", 402);
    }
    
    public void mockRefundError() {
        String payload = TestTemplateResourceLoader.load(STRIPE_ERROR_RESPONSE);
        setupResponse(payload, "/v1/refunds", 402);
    }

    public void mockUnauthorizedResponse() {
        Map<String, Object> unauthorizedResponse = ImmutableMap.of("error", ImmutableMap.of(
                "message", "Invalid API Key provided: sk_test_****",
                "type", "invalid_request_error"));
        setupResponse(new JSONObject(unauthorizedResponse).toString(), "/v1/tokens", 401);
    }

    public void mockCreateSource() {
        String payload = TestTemplateResourceLoader.load(STRIPE_CREATE_SOURCES_SUCCESS_RESPONSE);
        setupResponse(payload, "/v1/sources", 200);
    }

    public void mockAuthorisationFailed() {
        String payload = TestTemplateResourceLoader.load(STRIPE_AUTHORISATION_FAILED_RESPONSE);
        setupResponse(payload, "/v1/charges", 400);
    }

    public void mockCreateSourceWithThreeDSecureRequired() {
        String payload = TestTemplateResourceLoader.load(STRIPE_CREATE_SOURCES_3DS_REQUIRED_RESPONSE)
                .replace("{{three_d_secure_option}}","required");
        setupResponse(payload, "/v1/sources", 200);
    }

    public void mockCreate3dsSource() {
        String payload = TestTemplateResourceLoader.load(STRIPE_CREATE_3DS_SOURCES_RESPONSE)
                .replace("{{three_d_source_status}}", "pending");
        setupResponse("three_d_secure", payload, "/v1/sources", 200);
    }

    public void mockCancelCharge() {
        String payload = TestTemplateResourceLoader.load(STRIPE_REFUND_FULL_CHARGE_RESPONSE);
        setupResponse(payload, "/v1/refunds", 200);
    }

    public void mockRefund() {
        String payload = TestTemplateResourceLoader.load(STRIPE_REFUND_FULL_CHARGE_RESPONSE);
        setupResponse(payload, "/v1/refunds", 200);
    }


    public void mockTransferSuccess(String idempotencyKey) {
        String payload = TestTemplateResourceLoader.load(STRIPE_TRANSFER_RESPONSE);
        MappingBuilder builder = post(urlPathEqualTo("/v1/transfers"))
                .withHeader(CONTENT_TYPE, matching(APPLICATION_FORM_URLENCODED))
                .withHeader("Idempotency-Key", equalTo(idempotencyKey));

        Optional.ofNullable(idempotencyKey)
                .ifPresentOrElse(
                        key -> builder.withHeader("Idempotency-Key", equalTo(key)),
                        () -> builder.withHeader("Idempotency-Key", matching(".*"))
                );

        stubFor(builder
                .willReturn(aResponse().withHeader(CONTENT_TYPE, APPLICATION_JSON).withStatus(200).withBody(payload)));
    }

    public void mockTransferFailure() {
        String payload = TestTemplateResourceLoader.load(STRIPE_AUTHORISATION_FAILED_RESPONSE);
        setupResponse(payload, "/v1/transfers", 400);
    }
}
