package uk.gov.pay.connector.it.contract;

import org.mockserver.client.server.ForwardChainExpectation;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.model.HttpResponse;

import static java.lang.String.format;
import static javax.ws.rs.HttpMethod.POST;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.TEXT_XML;
import static org.eclipse.jetty.http.HttpStatus.OK_200;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.XPathBody.xpath;

public class SmartpayMockClient {
    private final MockServerClient mockClient;
    public static final String CAPTURE_SUCCESS_PAYLOAD = "<ns0:Envelope\n" +
            "    xmlns:ns0=\"http://schemas.xmlsoap.org/soap/envelope/\"\n" +
            "    xmlns:ns1=\"http://payment.services.adyen.com\"\n" +
            "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
            "    <ns0:Body>\n" +
            "        <ns1:captureResponse>\n" +
            "            <ns1:captureResult>\n" +
            "                <ns1:additionalData xsi:nil=\"true\" />\n" +
            "                <ns1:pspReference>8614440510830227</ns1:pspReference>\n" +
            "                <ns1:response>[capture-received]</ns1:response>\n" +
            "            </ns1:captureResult>\n" +
            "        </ns1:captureResponse>\n" +
            "    </ns0:Body>\n" +
            "</ns0:Envelope>";

    public static int UNKNOWN_STATUS_CODE = 3457;

    private final String transactionId;

    public SmartpayMockClient(int port, String transactionId) {
        this.transactionId = transactionId;
        this.mockClient = new MockServerClient("localhost", port);
    }

    public void respondWithStatusCodeAndPayloadWhenCapture(int statusCode, String payload) {
        whenCapture(transactionId)
                .respond(withStatusAndBody(statusCode, payload));
    }

    public void respondWithMalformedBody_WhenCapture(String transactionId) {
        whenCapture(transactionId)
                .respond(withStatusAndBody(OK_200, ">>>|<malformed xml/>|<<<"));
    }

    private ForwardChainExpectation whenCapture(String transactionId) {
        return mockClient.when(request()
                        .withMethod(POST)
                        .withBody(xpath(format("//modificationRequest/originalReference[text() = '%s']", transactionId)))
        );
    }

    private HttpResponse withStatusAndBody(int statusCode, String body) {
        return response()
                .withStatusCode(statusCode)
                .withHeader(CONTENT_TYPE, TEXT_XML)
                .withBody(body);
    }
}
