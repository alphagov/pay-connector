package uk.gov.pay.connector.rules;

import com.github.tomakehurst.wiremock.WireMockServer;

import static org.apache.hc.core5.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.hc.core5.http.HttpStatus.SC_CREATED;
import static org.apache.hc.core5.http.HttpStatus.SC_OK;
import static org.apache.hc.core5.http.HttpStatus.SC_UNAUTHORIZED;

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

    public void mockAuthorisationSuccessForRecurringPayment(String pspReferenceFromAdyen, String storedPaymentMethodId) {
        var responseBody = """
                {
                  "pspReference": "%s",
                  "resultCode": "Authorised",
                  "merchantReference": "string",
                  "additionalData": {
                    "tokenization.storedPaymentMethodId": "%s"
                    }
                }""".formatted(pspReferenceFromAdyen, storedPaymentMethodId);

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

    public void mock3dsAuthorisationResponse(String pspReferenceFromAdyen, String resultCode) {
        var responseBody = """
                {
                  "pspReference": "%s",
                  "resultCode": "%s"
                }""".formatted(
                pspReferenceFromAdyen,
                resultCode
        );
        setupPostResponse(responseBody, "/payments/details", SC_OK);
    }

    public void mock3dsAuthorisationResponseForRecurringPayment(
            String pspReferenceFromAdyen,
            String resultCode,
            String storedPaymentMethodId
    ) {
        var responseBody = """
                {
                  "pspReference": "%s",
                  "resultCode": "%s",
                  "additionalData": {
                    "tokenization.storedPaymentMethodId": "%s"
                    }
                }""".formatted(pspReferenceFromAdyen, resultCode, storedPaymentMethodId);
        setupPostResponse(responseBody, "/payments/details", SC_OK);
    }

    public void mock3dsAuthorisationClientError() {
        var responseBody = """
                {
                  "status": 400,
                  "errorCode": "702",
                  "message": "Problem with payment details",
                  "errorType": "validation",
                  "pspReference": "3ds-client-error-reference"
                }""";
        setupPostResponse(responseBody, "/payments/details", SC_BAD_REQUEST);
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

    public void mockAuthorisationRedirectShopper(String pspReferenceFromAdyen,
                                                 String redirectUrl,
                                                 String httpMethod,
                                                 String data) {
        var responseBody = """
                {
                  "pspReference": "%s",
                  "resultCode": "RedirectShopper",
                  "action": {
                    "paymentMethodType": "scheme",
                    "url": "%s",
                    "method": "%s",
                    "type": "redirect"
                    %s
                  }
                }""".formatted(pspReferenceFromAdyen, redirectUrl, httpMethod, data);

        setupPostResponse(responseBody, "/payments", SC_OK);
    }
    
    public void mock3dsAuthorisationRejected(String pspReferenceFromAdyen) {
        var responseBody = """
            {
              "pspReference": "%s",
              "refusalReason": "Expired Card",
              "resultCode": "Refused",
              "refusalReasonCode": "6"
            }""".formatted(pspReferenceFromAdyen);

        setupPostResponse(responseBody, "/payments/details", SC_OK);
    }
}
