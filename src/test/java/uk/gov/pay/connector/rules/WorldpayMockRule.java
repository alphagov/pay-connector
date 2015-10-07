package uk.gov.pay.connector.rules;

import com.google.common.io.Resources;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.UUID;

import static com.github.dreamhead.moco.Moco.by;
import static com.github.dreamhead.moco.Moco.header;
import static com.github.dreamhead.moco.Moco.status;
import static com.github.dreamhead.moco.Moco.uri;
import static com.github.dreamhead.moco.Moco.with;
import static javax.ws.rs.core.MediaType.TEXT_XML;

public class WorldpayMockRule extends MocoHttpsTestRule {

    public static final int port = 10107;

    public WorldpayMockRule() {
        super(port);
    }

    public void mockInquiryResponse(String gatewayTransactionId, String status) {
        String inquiryResponse = loadFromTemplate("inquiry-success-response.xml", gatewayTransactionId)
                .replace("{{status}}", status);
        paymentServiceResponse(inquiryResponse);
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

    private void paymentServiceResponse(String response) {
        httpServer
                .post(by(uri("/jsp/merchant/xml/paymentService.jsp")))
                .response(
                        status(200),
                        with(response),
                        header("Content-Type", TEXT_XML)
                );
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
