package uk.gov.pay.connector.rules;

import com.google.common.collect.ImmutableMap;
import com.google.gson.GsonBuilder;
import jakarta.xml.bind.DatatypeConverter;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.TEXT_XML;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.SQS_SEND_MESSAGE_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

public class SQSMockClient {

    /**
     * Mocks sending message to SQS  queue. Response includes MD5 of message body
     * SQSClient throws `com.amazonaws.AmazonClientException` exception if MD5 doesn't match based on request body
     * @param chargeId
     */
    public void mockSuccessfulSendChargeToQueue(String chargeId) {
        String body = new GsonBuilder()
                .create()
                .toJson(ImmutableMap.of("chargeId", chargeId));
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        md.update(body.getBytes());
        String md5OfBody = DatatypeConverter.printHexBinary(md.digest()).toLowerCase();

        String sendMessageResponse = load(SQS_SEND_MESSAGE_RESPONSE).replace("{{md5OfMessageBody}}", md5OfBody);
        sqsSuccessResponse(sendMessageResponse);
    }

    private void sqsSuccessResponse(String responseBody) {
        stubFor(
                post(urlPathEqualTo("/"))
                        .willReturn(
                                aResponse()
                                        .withHeader(CONTENT_TYPE, TEXT_XML)
                                        .withStatus(200)
                                        .withBody(responseBody)
                        )
        );
    }

    private void sqsErrorResponse(String responseBody) {
        stubFor(
                post(urlPathEqualTo("/capture-queue"))
                        .willReturn(
                                aResponse()
                                        .withHeader(CONTENT_TYPE, TEXT_XML)
                                        .withStatus(400)
                                        .withBody(responseBody)
                        )
        );
    }
}
