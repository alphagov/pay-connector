package uk.gov.pay.connector.rules;

import com.github.tomakehurst.wiremock.WireMockServer;

import static org.apache.http.HttpStatus.SC_OK;

public class AdyenCheckoutMockClient extends AdyenMockClient {

    public AdyenCheckoutMockClient(WireMockServer wireMockServer) {
        super(wireMockServer);
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
