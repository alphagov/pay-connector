package uk.gov.pay.connector.rules;

import com.google.common.io.Resources;
import org.mockserver.client.server.MockServerClient;

import java.io.IOException;
import java.nio.charset.Charset;

import static javax.ws.rs.HttpMethod.POST;
import static javax.ws.rs.core.MediaType.TEXT_XML;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static uk.gov.pay.connector.util.TransactionId.randomId;

public class WorldpayMockClient {

    private final MockServerClient mockClient;

    public WorldpayMockClient(int mockServerPort) {
        this.mockClient = new MockServerClient("localhost", mockServerPort);
    }

    public void mockInquiryResponse(String gatewayTransactionId, String status) {
        String inquiryResponse = loadFromTemplate("inquiry-success-response.xml", gatewayTransactionId)
                .replace("{{status}}", status);
        paymentServiceResponse(inquiryResponse);
    }

    public void mockAuthorisationSuccess() {
        String gatewayTransactionId = randomId();
        String authoriseResponse = loadFromTemplate("authorisation-success-response.xml", gatewayTransactionId);
        paymentServiceResponse(authoriseResponse);
    }

    public void mockAuthorisationFailure() {
        String gatewayTransactionId = randomId();
        String authoriseResponse = loadFromTemplate("authorisation-failed-response.xml", gatewayTransactionId);
        paymentServiceResponse(authoriseResponse);
    }

    public void mockCaptureResponse() {
        String gatewayTransactionId = randomId();
        String captureResponse = loadFromTemplate("capture-success-response.xml", gatewayTransactionId);
        paymentServiceResponse(captureResponse);
    }

    public void mockCancelResponse(String gatewayTransactionId) {
        String cancelResponse = loadFromTemplate("cancel-success-response.xml", gatewayTransactionId);
        paymentServiceResponse(cancelResponse);
    }

    public void mockErrorResponse() {
        String errorResponse = loadFromTemplate("error-response.xml", "");
        paymentServiceResponse(errorResponse);
    }

    private void paymentServiceResponse(String responseBody) {
        mockClient.when(request()
                        .withMethod(POST)
                        .withPath("/jsp/merchant/xml/paymentService.jsp")
        ).respond(response()
                .withStatusCode(200)
                .withBody(responseBody)
                .withHeader("Content-Type", TEXT_XML));
    }

    private String loadFromTemplate(String fileName, String gatewayTransactionId) {
        try {
            return Resources.toString(Resources.getResource("templates/worldpay/" + fileName), Charset.defaultCharset())
                    .replace("{{transactionId}}", gatewayTransactionId);
        } catch (IOException e) {
            throw new RuntimeException("Could not load template", e);
        }
    }
}
