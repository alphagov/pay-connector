package uk.gov.pay.connector.rules;

import com.google.common.io.Resources;

import java.io.IOException;
import java.nio.charset.Charset;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static java.util.UUID.randomUUID;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.TEXT_XML;

public class SmartpayMockClient {

    public void mockAuthorisationWithTransactionId(String transactionId) {
        String authoriseResponse = loadFromTemplate("authorisation-success-response.xml")
                .replace("{{pspReference}}", transactionId);
        paymentServiceResponse(authoriseResponse);
    }

    public void mockAuthorisationSuccess() {
        mockAuthorisationWithTransactionId(randomUUID().toString());
    }

    public void mockAuthorisationFailure() {
        String authoriseResponse = loadFromTemplate("authorisation-failed-response.xml");
        paymentServiceResponse(authoriseResponse);
    }

    public void mockCaptureSuccess() {
        mockCaptureSuccessWithTransactionId(randomUUID().toString());
    }

    public void mockCaptureSuccessWithTransactionId(String transactionId) {
        String captureResponse = loadFromTemplate("capture-success-response.xml")
                .replace("{{pspReference}}", transactionId);
        paymentServiceResponse(captureResponse);
    }

    public void mockCaptureError() {
        String errorResponse = loadFromTemplate("capture-error-response.xml");
        paymentServiceResponse(errorResponse);
    }

    public void mockCancel() {
        String cancelResponse = loadFromTemplate("cancel-success-response.xml");
        paymentServiceResponse(cancelResponse);
    }

    public void mockRefundSuccess() {
        String refundResponse = loadFromTemplate("refund-success-response.xml");
        paymentServiceResponse(refundResponse);
    }

    public void mockRefundError() {
        String refundResponse = loadFromTemplate("refund-error-response.xml");
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

    private String loadFromTemplate(String fileName) {
        try {
            return Resources.toString(Resources.getResource("templates/smartpay/" + fileName), Charset.defaultCharset());
        } catch (IOException e) {
            throw new RuntimeException("Could not load template", e);
        }
    }
}
