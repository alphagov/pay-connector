package uk.gov.pay.connector.rules;

import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_AUTHORISATION_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_CREATE_SOURCE_SUCCESS_RESPONSE;

public class StripeMockClient {
    public void mockCreateToken() {
        String payload = TestTemplateResourceLoader.load(STRIPE_CREATE_SOURCE_SUCCESS_RESPONSE);
        paymentServiceResponse(payload, "/v1/tokens", APPLICATION_FORM_URLENCODED);
    }

    private void paymentServiceResponse(String responseBody, String path, String mediaType) {
        stubFor(post(urlPathEqualTo(path)).withHeader(CONTENT_TYPE, matching(mediaType))
                .willReturn(aResponse().withHeader(CONTENT_TYPE, APPLICATION_JSON).withStatus(200).withBody(responseBody)));
    }

    public void mockCreateCharge() {
        String payload = TestTemplateResourceLoader.load(STRIPE_AUTHORISATION_SUCCESS_RESPONSE);
        paymentServiceResponse(payload, "/v1/charges", APPLICATION_FORM_URLENCODED);
    }
}
