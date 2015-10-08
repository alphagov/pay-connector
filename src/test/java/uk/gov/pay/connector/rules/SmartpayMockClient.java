package uk.gov.pay.connector.rules;

import com.google.common.io.Resources;
import org.mockserver.client.server.MockServerClient;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.UUID;

import static javax.ws.rs.HttpMethod.POST;
import static javax.ws.rs.core.MediaType.TEXT_XML;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class SmartpayMockClient {

    private final MockServerClient mockClient;

    public SmartpayMockClient(int mockServerPort) {
        this.mockClient = new MockServerClient("localhost", mockServerPort);
    }

    public void mockAuthorisationSuccess() {
        String gatewayTransactionId = UUID.randomUUID().toString();
        String authoriseResponse = loadFromTemplate("authorisation-success-response.xml", gatewayTransactionId);
        paymentServiceResponse(authoriseResponse);
    }

    public void mockAuthorisationFailure() {
        String gatewayTransactionId = UUID.randomUUID().toString();
        String authoriseResponse = loadFromTemplate("authorisation-failed-response.xml", gatewayTransactionId);
        paymentServiceResponse(authoriseResponse);
    }

    public void mockCaptureResponse() {
        String gatewayTransactionId = UUID.randomUUID().toString();
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
                        .withPath("/pal/servlet/soap/Payment")
        ).respond(response()
                .withStatusCode(200)
                .withBody(responseBody)
                .withHeader("Content-Type", TEXT_XML));
    }

    private String loadFromTemplate(String fileName, String gatewayTransactionId) {
        try {
            return Resources.toString(Resources.getResource("templates/smartpay/" + fileName), Charset.defaultCharset())
                    .replace("{{transactionId}}", gatewayTransactionId);
        } catch (IOException e) {
            throw new RuntimeException("Could not load template", e);
        }
    }
}
