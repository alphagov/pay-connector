package uk.gov.pay.connector.rules;

import com.github.tomakehurst.wiremock.WireMockServer;

import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;

public class AdyenCheckoutMockClient extends AdyenMockClient {

    public AdyenCheckoutMockClient(WireMockServer wireMockServer) {
        super(wireMockServer);
    }

    public void mockAuthorisationSuccess(String pspReferenceFromAdyen) {
        var responseBody = """
                {
                  "pspReference": "%s",
                  "resultCode": "Authorised",
                  "merchantReference": "string"
                }""".formatted(pspReferenceFromAdyen);

        setupPostResponse(responseBody, "/payments", SC_OK);
    }

    public void mockAuthorisationRejected(String pspReferenceFromAdyen) {
        var responseBody = """
                {
                  "pspReference": "%s",
                  "refusalReason": "Expired Card",
                  "resultCode": "Refused",
                  "refusalReasonCode": "6"
                }""".formatted(pspReferenceFromAdyen);
        setupPostResponse(responseBody, "/payments", SC_OK);
    }

    public void mockAuthorisationError(String pspReferenceFromAdyen) {
        var responseBody = """
                {
                  "pspReference": "%s",
                  "refusalReason": "Acquirer Error",
                  "resultCode": "Error",
                  "refusalReasonCode": "4"
                }""".formatted(pspReferenceFromAdyen);
        setupPostResponse(responseBody, "/payments", SC_OK);
    }

    public void mockCancellationSuccess(String pspReferenceFromAdyen, String paymentPspReference) {
        var responseBody = """
                {
                  "pspReference": "%s",
                  "merchantAccount": "adyen-test-merchant-account-id",
                  "paymentPspReference": "%s",
                  "reference": "864vqloqrm71jn89r4bjkhvkv2",
                  "status": "received"
                }""".formatted(pspReferenceFromAdyen, paymentPspReference);
        var path = "/payments/%s/cancels".formatted(paymentPspReference);
        setupPostResponse(responseBody, path, SC_CREATED);
    }

    public void mockCancellationFailure(String paymentPspReference) {
        var responseBody = """
                {
                  "status": 401,
                  "errorCode": "000",
                  "message": "HTTP Status Response - Unauthorized",
                  "errorType": "security"
                }""";
        var path = "/payments/%s/cancels".formatted(paymentPspReference);
        setupPostResponse(responseBody, path, SC_UNAUTHORIZED);
    }

    public void mockRefundSuccess(String pspReferenceFromAdyen, String paymentPspReference) {
        var responseBody = """
                {
                  "pspReference": "%s",
                  "merchantAccount": "adyen-test-merchant-account-id",
                  "paymentPspReference": "%s",
                  "reference": "864vqloqrm71jn89r4bjkhvkv2",
                  "status": "received"
                }""".formatted(pspReferenceFromAdyen, paymentPspReference);
        var path = "/payments/%s/refunds".formatted(paymentPspReference);
        setupPostResponse(responseBody, path, SC_CREATED);
    }

    public void mockRefundError(String paymentPspReference) {
        var responseBody = """
                {
                  "status": 403,
                  "errorCode": "901",
                  "message": "Invalid Merchant Account",
                  "errorType": "security"
                }""";
        var path = "/payments/%s/refunds".formatted(paymentPspReference);
        setupPostResponse(responseBody, path, 403);
    }
}
