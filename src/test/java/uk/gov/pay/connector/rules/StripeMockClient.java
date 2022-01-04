package uk.gov.pay.connector.rules;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_AUTHORISATION_FAILED_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_ERROR_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_GET_PAYMENT_INTENT_WITH_3DS_AUTHORISED_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_PAYMENT_INTENT_CANCEL_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_PAYMENT_INTENT_CAPTURE_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_PAYMENT_INTENT_REQUIRES_3DS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_PAYMENT_INTENT_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_PAYMENT_METHOD_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_REFUND_FULL_CHARGE_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_TRANSFER_RESPONSE;

public class StripeMockClient {

    private WireMockServer wireMockServer;

    public StripeMockClient(WireMockServer wireMockServer) {
        this.wireMockServer = wireMockServer;
    }

    private void setupResponse(String responseBody, String path, int status) {
        wireMockServer.stubFor(post(urlPathEqualTo(path)).withHeader(CONTENT_TYPE, matching(APPLICATION_FORM_URLENCODED))
                .willReturn(aResponse().withHeader(CONTENT_TYPE, APPLICATION_JSON).withStatus(status).withBody(responseBody)));
    }

    private void setupGetResponse(String responseBody, String path, int status) {
        wireMockServer.stubFor(get(urlPathEqualTo(path))
                .willReturn(aResponse().withHeader(CONTENT_TYPE, APPLICATION_JSON).withStatus(status).withBody(responseBody)));
    }

    public void mockRefundError() {
        String payload = TestTemplateResourceLoader.load(STRIPE_ERROR_RESPONSE);
        setupResponse(payload, "/v1/refunds", 402);
    }

    public void mockAuthorisationFailedWithPaymentIntents() {
        String payload = TestTemplateResourceLoader.load(STRIPE_AUTHORISATION_FAILED_RESPONSE);
        setupResponse(payload, "/v1/payment_methods", 400);
    }

    public void mockCancelPaymentIntent(String paymentIntentId) {
        String payload = TestTemplateResourceLoader.load(STRIPE_PAYMENT_INTENT_CANCEL_RESPONSE);
        setupResponse(payload, "/v1/payment_intents/" + paymentIntentId + "/cancel", 200);
    }

    public void mockRefund() {
        String payload = TestTemplateResourceLoader.load(STRIPE_REFUND_FULL_CHARGE_RESPONSE);
        setupResponse(payload, "/v1/refunds", 200);
    }

    public void mockTransferSuccess() {
        String payload = TestTemplateResourceLoader.load(STRIPE_TRANSFER_RESPONSE);
        MappingBuilder builder = post(urlPathEqualTo("/v1/transfers"))
                .withHeader(CONTENT_TYPE, matching(APPLICATION_FORM_URLENCODED))
                .withHeader("Idempotency-Key", matching(".*"));

        wireMockServer.stubFor(builder
                .willReturn(aResponse().withHeader(CONTENT_TYPE, APPLICATION_JSON).withStatus(200).withBody(payload)));
    }

    public void mockTransferFailure() {
        String payload = TestTemplateResourceLoader.load(STRIPE_AUTHORISATION_FAILED_RESPONSE);
        setupResponse(payload, "/v1/transfers", 400);
    }

    public void mockGetPaymentIntent(String paymentIntentId) {
        String payload = TestTemplateResourceLoader.load(STRIPE_PAYMENT_INTENT_CAPTURE_SUCCESS_RESPONSE);
        setupGetResponse(payload, "/v1/payment_intents/" + paymentIntentId, 200);
    }
    
    public void mockGet3DSAuthenticatedPaymentIntent(String paymentIntentId) {
        String payload = TestTemplateResourceLoader.load(STRIPE_GET_PAYMENT_INTENT_WITH_3DS_AUTHORISED_RESPONSE);
        setupGetResponse(payload, "/v1/payment_intents/" + paymentIntentId, 200);
    }

    public void mockCreatePaymentIntent() {
        String payload = TestTemplateResourceLoader.load(STRIPE_PAYMENT_INTENT_SUCCESS_RESPONSE);
        setupResponse(payload, "/v1/payment_intents", 200);
    }

    public void mockCreatePaymentIntentRequiring3DS() {
        String payload = TestTemplateResourceLoader.load(STRIPE_PAYMENT_INTENT_REQUIRES_3DS_RESPONSE);
        setupResponse(payload, "/v1/payment_intents", 200);
    }

    public void mockCreatePaymentMethod() {
        String payload = TestTemplateResourceLoader.load(STRIPE_PAYMENT_METHOD_SUCCESS_RESPONSE);
        setupResponse(payload, "/v1/payment_methods", 200);
    }

    public void mockTransferReversal(String transferid) {
        String payload = TestTemplateResourceLoader.load(STRIPE_TRANSFER_RESPONSE);
        setupResponse(payload, "/v1/transfers/" + transferid + "/reversals", 200);
    }
}
