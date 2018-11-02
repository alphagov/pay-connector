package uk.gov.pay.connector.rules;

import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_AUTHORISATION_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_CREATE_SOURCE_SUCCESS_RESPONSE;

public class StripeMockClient {
    public void mockCreateSource() {
        String payload = TestTemplateResourceLoader.load(STRIPE_CREATE_SOURCE_SUCCESS_RESPONSE);
        paymentServiceResponse(payload, "/v1/sources");
    }

    private void paymentServiceResponse(String responseBody, String path) {
        stubFor(
                post(urlPathEqualTo(path))
                        .willReturn(
                                aResponse()
                                        .withHeader(CONTENT_TYPE, APPLICATION_JSON)
                                        .withStatus(200)
                                        .withBody(responseBody)
                        )
        );
    }

    public void mockCreateCharge() {
        String payload = TestTemplateResourceLoader.load(STRIPE_AUTHORISATION_SUCCESS_RESPONSE);
        paymentServiceResponse(payload, "/v1/charges");
    }
}
