package uk.gov.pay.connector.rules;

import com.google.common.collect.ImmutableMap;
import org.json.JSONObject;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_AUTHORISATION_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_CAPTURE_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_CREATE_SOURCES_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_CREATE_TOKEN_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_ERROR_RESPONSE;

public class StripeMockClient {
    public void mockCreateToken() {
        String payload = TestTemplateResourceLoader.load(STRIPE_CREATE_TOKEN_SUCCESS_RESPONSE);
        setupResponse(payload, "/v1/tokens", 200);
    }

    private void setupResponse(String responseBody, String path, int status) {
        stubFor(post(urlPathEqualTo(path)).withHeader(CONTENT_TYPE, matching(APPLICATION_FORM_URLENCODED))
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
        setupResponse(payload, "/v1/charges/" + gatewayTransactionId + "/capture", 401);
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
}
