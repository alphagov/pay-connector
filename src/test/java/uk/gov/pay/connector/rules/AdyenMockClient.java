package uk.gov.pay.connector.rules;

import com.github.tomakehurst.wiremock.WireMockServer;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_OK;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.ADYEN_ERROR_RESPONSE;

public class AdyenMockClient {
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
        setupPostResponse(responseBody, path, SC_INTERNAL_SERVER_ERROR);
    }

    public void mockAuthorisationSuccess(String pspReferenceFromAdyen) {
        String responseBody = """
                {
                  "pspReference": "%s",
                  "resultCode": "Authorised",
                  "merchantReference": "string"
                }""".formatted(pspReferenceFromAdyen);

        setupPostResponse(responseBody, "/payments", SC_OK);
    }

    public void mockAuthorisationRejected(String pspReferenceFromAdyen) {
        String responseBody = """
                {
                  "pspReference": "%s",
                  "refusalReason": "Expired Card",
                  "resultCode": "Refused",
                  "refusalReasonCode": "6"
                }""".formatted(pspReferenceFromAdyen);
        setupPostResponse(responseBody, "/payments", SC_OK);
    }

    public void mockAuthorisationError(String pspReferenceFromAdyen) {
        String responseBody = """
                {
                  "pspReference": "%s",
                  "refusalReason": "Acquirer Error",
                  "resultCode": "Error",
                  "refusalReasonCode": "4"
                }""".formatted(pspReferenceFromAdyen);
        setupPostResponse(responseBody, "/payments", SC_OK);
    }
}
