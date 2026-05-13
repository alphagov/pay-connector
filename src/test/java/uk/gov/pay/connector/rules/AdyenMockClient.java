package uk.gov.pay.connector.rules;

import com.github.tomakehurst.wiremock.WireMockServer;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.apache.http.HttpStatus.SC_BAD_GATEWAY;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.ADYEN_ERROR_RESPONSE;

public abstract class AdyenMockClient {
    protected WireMockServer wireMockServer;

    public AdyenMockClient(WireMockServer wireMockServer) {
        this.wireMockServer = wireMockServer;
    }

    protected void setupPostResponse(String responseBody, String path, int status) {
        wireMockServer.stubFor(post(urlPathEqualTo(path))
                .withHeader(CONTENT_TYPE, matching(APPLICATION_JSON))
                .willReturn(aResponse()
                        .withHeader(CONTENT_TYPE, APPLICATION_JSON)
                        .withStatus(status)
                        .withBody(responseBody)));
    }
    
    public void mockError(String path) {
        String responseBody = TestTemplateResourceLoader.load(ADYEN_ERROR_RESPONSE);
        setupPostResponse(responseBody, path, SC_BAD_GATEWAY);
    }
}
