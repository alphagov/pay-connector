package uk.gov.pay.connector.rules;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_AUTHORISATION_FAILED_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_AUTHORISATION_FAILED_RESPONSE_USER_NOT_PRESENT_PAYMENT_NOT_RETRIABLE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_AUTHORISATION_FAILED_RESPONSE_USER_NOT_PRESENT_PAYMENT_RETRIABLE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_CUSTOMER_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_ERROR_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_GET_PAYMENT_INTENT_WITH_3DS_AUTHORISED_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_PAYMENT_INTENT_AUTHORISATION_REJECTED_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_PAYMENT_INTENT_CANCEL_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_PAYMENT_INTENT_CAPTURE_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_PAYMENT_INTENT_ERROR_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_PAYMENT_INTENT_REQUIRES_3DS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_PAYMENT_INTENT_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_PAYMENT_INTENT_SUCCESS_RESPONSE_WITH_CUSTOMER;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_PAYMENT_METHOD_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_REFUND_FULL_CHARGE_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_SEARCH_PAYMENT_INTENTS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_TOKEN_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_TRANSFER_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

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
        String payload = load(STRIPE_ERROR_RESPONSE);
        setupResponse(payload, "/v1/refunds", 402);
    }

    public void mockCreatePaymentMethodAuthorisationRejected() {
        String payload = load(STRIPE_AUTHORISATION_FAILED_RESPONSE);
        setupResponse(payload, "/v1/payment_methods", 400);
    }
    
    public void mockCreatePaymentIntentAuthorisationRejected() {
        String payload = load(STRIPE_PAYMENT_INTENT_AUTHORISATION_REJECTED_RESPONSE);
        setupResponse(payload, "/v1/payment_intents", 402);
    }

    public void mockCreatePaymentIntentAuthorisationError() {
        String payload = load(STRIPE_PAYMENT_INTENT_ERROR_RESPONSE);
        setupResponse(payload, "/v1/payment_intents", 402);
    }

    public void mockAuthorisationFailedPaymentIntentAndRetriableForUserNotPresentPayment() {
        String payload = load(STRIPE_AUTHORISATION_FAILED_RESPONSE_USER_NOT_PRESENT_PAYMENT_RETRIABLE);
        setupResponse(payload, "/v1/payment_intents", 400);
    }

    public void mockAuthorisationFailedPaymentIntentAndNonRetriableForUserNotPresentPayment() {
        String payload = load(STRIPE_AUTHORISATION_FAILED_RESPONSE_USER_NOT_PRESENT_PAYMENT_NOT_RETRIABLE);
        setupResponse(payload, "/v1/payment_intents", 400);
    }

    public void mockAuthorisationErrorForUserNotPresentPayment() {
        setupResponse(null, "/v1/payment_intents", 500);
    }

    public void mockCancelPaymentIntent(String paymentIntentId) {
        String payload = load(STRIPE_PAYMENT_INTENT_CANCEL_RESPONSE);
        setupResponse(payload, "/v1/payment_intents/" + paymentIntentId + "/cancel", 200);
    }

    public void mockRefund() {
        String payload = load(STRIPE_REFUND_FULL_CHARGE_RESPONSE);
        setupResponse(payload, "/v1/refunds", 200);
    }

    public void mockTransferSuccess() {
        String payload = load(STRIPE_TRANSFER_RESPONSE);
        MappingBuilder builder = post(urlPathEqualTo("/v1/transfers"))
                .withHeader(CONTENT_TYPE, matching(APPLICATION_FORM_URLENCODED))
                .withHeader("Idempotency-Key", matching(".*"));

        wireMockServer.stubFor(builder
                .willReturn(aResponse().withHeader(CONTENT_TYPE, APPLICATION_JSON).withStatus(200).withBody(payload)));
    }

    public void mockTransferFailure() {
        String payload = load(STRIPE_AUTHORISATION_FAILED_RESPONSE);
        setupResponse(payload, "/v1/transfers", 400);
    }

    public void mockGetPaymentIntent(String paymentIntentId) {
        String payload = load(STRIPE_PAYMENT_INTENT_CAPTURE_SUCCESS_RESPONSE);
        setupGetResponse(payload, "/v1/payment_intents/" + paymentIntentId, 200);
    }
    
    public void mockGet3DSAuthenticatedPaymentIntent(String paymentIntentId) {
        String payload = load(STRIPE_GET_PAYMENT_INTENT_WITH_3DS_AUTHORISED_RESPONSE);
        setupGetResponse(payload, "/v1/payment_intents/" + paymentIntentId, 200);
    }

    public void mockCreatePaymentIntent() {
        String payload = load(STRIPE_PAYMENT_INTENT_SUCCESS_RESPONSE);
        setupResponse(payload, "/v1/payment_intents", 200);
    }

    public void mockCreatePaymentIntentWithCustomer() {
        String payload = load(STRIPE_PAYMENT_INTENT_SUCCESS_RESPONSE_WITH_CUSTOMER);
        setupResponse(payload, "/v1/payment_intents", 200);
    }

    public void mockCreatePaymentIntentRequiring3DS() {
        String payload = load(STRIPE_PAYMENT_INTENT_REQUIRES_3DS_RESPONSE);
        setupResponse(payload, "/v1/payment_intents", 200);
    }

    public void mockCreatePaymentMethod() {
        String payload = load(STRIPE_PAYMENT_METHOD_SUCCESS_RESPONSE);
        setupResponse(payload, "/v1/payment_methods", 200);
    }
    
    public void mockCreateCustomer() {
        String payload = load(STRIPE_CUSTOMER_SUCCESS_RESPONSE);
        setupResponse(payload, "/v1/customers", 200);
    }
    
    public void mockCreateToken() {
        String payload = load(STRIPE_TOKEN_SUCCESS_RESPONSE);
        setupResponse(payload, "/v1/tokens", 200);
    }

    public void mockCreatePaymentIntentDelayedResponse() {
        String responseBody = load(STRIPE_PAYMENT_INTENT_SUCCESS_RESPONSE);
        String path = "/v1/payment_intents";
        wireMockServer.stubFor(post(urlPathEqualTo(path))
                .withHeader(CONTENT_TYPE, matching(APPLICATION_FORM_URLENCODED))
                .willReturn(aResponse().withHeader(CONTENT_TYPE, APPLICATION_JSON)
                        .withStatus(200)
                        .withFixedDelay(100)
                        .withBody(responseBody)));
    }
    
    public void mockSearchPaymentIntentsByMetadata(String chargeExternalId) {
        String responsePayload = load((STRIPE_SEARCH_PAYMENT_INTENTS_RESPONSE));
        wireMockServer.stubFor(get(urlPathEqualTo("/v1/payment_intents/search"))
                .withQueryParam("query", equalTo(String.format("metadata['govuk_pay_transaction_external_id']:'%s'", chargeExternalId)))
                .willReturn(aResponse().withHeader(CONTENT_TYPE, APPLICATION_JSON)
                        .withStatus(200)
                        .withBody(responsePayload)));
    }
}
