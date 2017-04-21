package uk.gov.pay.connector.rules;

import com.google.common.io.Resources;

import java.io.IOException;
import java.nio.charset.Charset;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.TEXT_XML;
import static uk.gov.pay.connector.util.TransactionId.randomId;

public class WorldpayMockClient {

    public WorldpayMockClient() {
    }

    public void mockAuthorisationSuccess() {
        String gatewayTransactionId = randomId();
        String authoriseResponse = loadFromTemplate("authorisation-success-response.xml");
        paymentServiceResponse(authoriseResponse);
    }

    public void mockAuthorisationRequires3ds() {
        String authorise3dsResponse = loadFromTemplate("3ds-response.xml");
        paymentServiceResponse(authorise3dsResponse);
    }

    public void mockAuthorisationFailure() {
        String gatewayTransactionId = randomId();
        String authoriseResponse = loadFromTemplate("authorisation-failed-response.xml");
        paymentServiceResponse(authoriseResponse);
    }

    public void mockCaptureSuccess() {
        String gatewayTransactionId = randomId();
        String captureResponse = loadFromTemplate("capture-success-response.xml");
        paymentServiceResponse(captureResponse);
    }

    public void mockCaptureError() {
        String gatewayTransactionId = randomId();
        String captureResponse = loadFromTemplate("capture-error-response.xml");
        paymentServiceResponse(captureResponse);
    }

    public void mockCancelSuccess() {
        String cancelResponse = loadFromTemplate("cancel-success-response.xml");
        paymentServiceResponse(cancelResponse);
    }

    public void mockCancelError() {
        String cancelResponse = loadFromTemplate("cancel-error-response.xml");
        paymentServiceResponse(cancelResponse);
    }

    public void mockCancelSuccessOnlyFor(String gatewayTransactionId) {
        String cancelSuccessResponse = loadFromTemplate("cancel-success-response.xml");
        String bodyMatchXpath = "//orderModification[@orderCode = '" + gatewayTransactionId + "']";
        bodyMatchingPaymentServiceResponse(bodyMatchXpath, cancelSuccessResponse);

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
        //FIXME - This mocking approach is very poor. Needs to be revisited. Story PP-900 created.
        stubFor(
                post(urlPathEqualTo("/jsp/merchant/xml/paymentService.jsp"))
                        .willReturn(
                                aResponse()
                                        .withHeader(CONTENT_TYPE, TEXT_XML)
                                        .withStatus(200)
                                        .withBody(responseBody)
                        )
        );
    }

    private void bodyMatchingPaymentServiceResponse(String xpathContent, String responseBody) {
        stubFor(
                post(urlPathEqualTo("/jsp/merchant/xml/paymentService.jsp"))
                        .withRequestBody(matchingXPath(xpathContent))
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
            return Resources.toString(Resources.getResource("templates/worldpay/" + fileName), Charset.defaultCharset());
        } catch (IOException e) {
            throw new RuntimeException("Could not load template", e);
        }
    }
}
