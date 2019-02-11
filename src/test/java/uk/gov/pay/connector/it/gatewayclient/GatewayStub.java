package uk.gov.pay.connector.it.gatewayclient;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static java.lang.String.format;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.TEXT_XML;
import static org.eclipse.jetty.http.HttpStatus.OK_200;

public class GatewayStub {
    private static final String AUTH_SUCCESS_PAYLOAD = "<authorise success response/>";
    private static final String CAPTURE_SUCCESS_PAYLOAD = "<ns0:Envelope\n" +
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
    private static final String MALFORMED_XML_PAYLOAD = ">>>|<malformed xml/>|<<<";

    private static final int UNKNOWN_STATUS_CODE = 999;
    private final String transactionId;

    public GatewayStub(String transactionId) {
        this.transactionId = transactionId;
    }

    public void respondWithStatusCodeAndPayloadWhenCapture(int statusCode, String payload) {
        respondWithStatusCodeAndPayloadWhenCapture(statusCode, payload, -1);
    }

    public void respondWithStatusCodeAndPayloadWhenCapture(int statusCode, String payload, int timeout) {
        ResponseDefinitionBuilder responseDefBuilder = aResponse()
                .withHeader(CONTENT_TYPE, TEXT_XML)
                .withStatus(statusCode)
                .withBody(payload);
        if (timeout >= 0) {
            responseDefBuilder.withFixedDelay(timeout);
        }

        stubFor(
                post(urlPathEqualTo("/pal/servlet/soap/Payment"))
                        .withRequestBody(
                                matching(format(".*<.*originalReference.*>%s</originalReference>.*", transactionId))
                        )
                        .willReturn(
                                responseDefBuilder
                        )
        );
    }

    public void respondWithStatusCodeAndPayloadWhenCardAuth(int statusCode, String payload) {
        respondWithStatusCodeAndPayloadWhenCardAuth(statusCode, payload, -1);
    }

    public void respondWithStatusCodeAndPayloadWhenCardAuth(int statusCode, String payload, int timeout) {
        ResponseDefinitionBuilder responseDefBuilder = aResponse()
                .withHeader(CONTENT_TYPE, TEXT_XML)
                .withStatus(statusCode)
                .withBody(payload);
        if (timeout >= 0) {
            responseDefBuilder.withFixedDelay(timeout);
        }

        stubFor(
                post(urlPathEqualTo("/pal/servlet/soap/Payment"))
                        .withRequestBody(
                                matching(".*<.*authorise.*>.*</.*authorise>.*")
                        )
                        .willReturn(
                                responseDefBuilder
                        )
        );
    }

    public void respondWithSuccessWhenCapture() {
        respondWithStatusCodeAndPayloadWhenCapture(OK_200, CAPTURE_SUCCESS_PAYLOAD);
    }

    public void respondWithUnexpectedResponseCodeWhenCapture() {
        respondWithStatusCodeAndPayloadWhenCapture(UNKNOWN_STATUS_CODE, CAPTURE_SUCCESS_PAYLOAD);
    }

    public void respondWithUnexpectedResponseCodeWhenCardAuth() {
        respondWithStatusCodeAndPayloadWhenCardAuth(UNKNOWN_STATUS_CODE, AUTH_SUCCESS_PAYLOAD);
    }

    public void respondWithMalformedBodyWhenCapture() {
        respondWithStatusCodeAndPayloadWhenCapture(OK_200, MALFORMED_XML_PAYLOAD);
    }

    public void respondWithTimeoutWhenCapture() {
        respondWithStatusCodeAndPayloadWhenCapture(OK_200, CAPTURE_SUCCESS_PAYLOAD, 10);
    }
}
