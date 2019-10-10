package uk.gov.pay.connector.rules;

import com.github.tomakehurst.wiremock.http.Fault;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.UUID.randomUUID;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.TEXT_XML;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.SMARTPAY_3DS_AUTHORISATION_FAILED_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.SMARTPAY_3DS_AUTHORISATION_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.SMARTPAY_AUTHORISATION_3DS_REQUIRED_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.SMARTPAY_AUTHORISATION_FAILED_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.SMARTPAY_AUTHORISATION_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.SMARTPAY_CANCEL_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.SMARTPAY_CAPTURE_ERROR_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.SMARTPAY_CAPTURE_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.SMARTPAY_REFUND_ERROR_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.SMARTPAY_REFUND_SUCCESS_RESPONSE;

public class SmartpayMockClient {

    public void mockAuthorisationWithTransactionId(String transactionId) {
        String authoriseResponse = TestTemplateResourceLoader.load(SMARTPAY_AUTHORISATION_SUCCESS_RESPONSE)
                .replace("{{pspReference}}", transactionId);
        paymentServiceResponse(authoriseResponse);
    }

    public void mockAuthorisationSuccess() {
        mockAuthorisationWithTransactionId(randomUUID().toString());
    }

    public void mock3dsAuthorisationSuccess() {
        mock3dsAuthorisationWithTransactionId(randomUUID().toString());
    }

    private void mock3dsAuthorisationWithTransactionId(String transactionId) {
        String authoriseResponse = TestTemplateResourceLoader.load(SMARTPAY_3DS_AUTHORISATION_SUCCESS_RESPONSE)
                .replace("{{pspReference}}", transactionId);
        paymentServiceResponse(authoriseResponse);
    }

    public void mockAuthorisation3dsRequired() {
        String authoriseResponse = TestTemplateResourceLoader.load(SMARTPAY_AUTHORISATION_3DS_REQUIRED_RESPONSE)
                .replace("{{pspReference}}", randomUUID().toString());
        paymentServiceResponse(authoriseResponse);
    }

    public void mockAuthorisationFailure() {
        String authoriseResponse = TestTemplateResourceLoader.load(SMARTPAY_AUTHORISATION_FAILED_RESPONSE);
        paymentServiceResponse(authoriseResponse);
    }
    
    public void mock3dsAuthorisationFailure() {
        String authorise3dsResponse = TestTemplateResourceLoader.load(SMARTPAY_3DS_AUTHORISATION_FAILED_RESPONSE);
        paymentServiceResponse(authorise3dsResponse);
    }

    public void mockCaptureSuccess() {
        mockCaptureSuccessWithTransactionId(randomUUID().toString());
    }

    public void mockCaptureSuccessWithTransactionId(String transactionId) {
        String captureResponse = TestTemplateResourceLoader.load(SMARTPAY_CAPTURE_SUCCESS_RESPONSE)
                .replace("{{pspReference}}", transactionId);
        paymentServiceResponse(captureResponse);
    }

    public void mockCaptureError() {
        String errorResponse = TestTemplateResourceLoader.load(SMARTPAY_CAPTURE_ERROR_RESPONSE);
        paymentServiceResponse(errorResponse);
    }

    public void mockCancel() {
        String cancelResponse = TestTemplateResourceLoader.load(SMARTPAY_CANCEL_SUCCESS_RESPONSE);
        paymentServiceResponse(cancelResponse);
    }

    public void mockServerFault() {
        stubFor(
                post(urlPathEqualTo("/pal/servlet/soap/Payment"))
                        .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))
        );
    }

    public void mockRefundSuccess() {
        String refundResponse = TestTemplateResourceLoader.load(SMARTPAY_REFUND_SUCCESS_RESPONSE);
        paymentServiceResponse(refundResponse);
    }

    public void mockRefundError() {
        String refundResponse = TestTemplateResourceLoader.load(SMARTPAY_REFUND_ERROR_RESPONSE);
        paymentServiceResponse(refundResponse);
    }

    private void paymentServiceResponse(String responseBody) {
        stubFor(
                post(urlPathEqualTo("/pal/servlet/soap/Payment"))
                        .willReturn(
                                aResponse()
                                        .withHeader(CONTENT_TYPE, TEXT_XML)
                                        .withStatus(200)
                                        .withBody(responseBody)
                        )
        );
    }
}
