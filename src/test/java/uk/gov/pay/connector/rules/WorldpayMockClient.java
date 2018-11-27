package uk.gov.pay.connector.rules;

import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingXPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.TEXT_XML;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_3DS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_AUTHORISATION_FAILED_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_CANCEL_ERROR_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_CANCEL_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_CAPTURE_ERROR_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_CAPTURE_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_REFUND_ERROR_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_REFUND_SUCCESS_RESPONSE;

public class WorldpayMockClient {

    public WorldpayMockClient() {
    }

    public void mockAuthorisationSuccess() {
        String authoriseResponse = TestTemplateResourceLoader.load(WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE);
        paymentServiceResponse(authoriseResponse);
    }

    public void mockAuthorisationRequires3ds() {
        String authorise3dsResponse = TestTemplateResourceLoader.load(WORLDPAY_3DS_RESPONSE);
        paymentServiceResponse(authorise3dsResponse);
    }

    public void mockAuthorisationFailure() {
        String authoriseResponse = TestTemplateResourceLoader.load(WORLDPAY_AUTHORISATION_FAILED_RESPONSE);
        paymentServiceResponse(authoriseResponse);
    }

    public void mockCaptureSuccess() {
        String captureResponse = TestTemplateResourceLoader.load(WORLDPAY_CAPTURE_SUCCESS_RESPONSE);
        paymentServiceResponse(captureResponse);
    }

    public void mockCaptureError() {
        String captureResponse = TestTemplateResourceLoader.load(WORLDPAY_CAPTURE_ERROR_RESPONSE);
        paymentServiceResponse(captureResponse);
    }

    public void mockCancelSuccess() {
        String cancelResponse = TestTemplateResourceLoader.load(WORLDPAY_CANCEL_SUCCESS_RESPONSE);
        paymentServiceResponse(cancelResponse);
    }

    public void mockCancelError() {
        String cancelResponse = TestTemplateResourceLoader.load(WORLDPAY_CANCEL_ERROR_RESPONSE);
        paymentServiceResponse(cancelResponse);
    }

    public void mockCancelSuccessOnlyFor(String gatewayTransactionId) {
        String cancelSuccessResponse = TestTemplateResourceLoader.load(WORLDPAY_CANCEL_SUCCESS_RESPONSE);
        String bodyMatchXpath = "//orderModification[@orderCode = '" + gatewayTransactionId + "']";
        bodyMatchingPaymentServiceResponse(bodyMatchXpath, cancelSuccessResponse);

    }

    public void mockRefundSuccess() {
        String refundResponse = TestTemplateResourceLoader.load(WORLDPAY_REFUND_SUCCESS_RESPONSE);
        paymentServiceResponse(refundResponse);
    }

    public void mockRefundError() {
        String refundResponse = TestTemplateResourceLoader.load(WORLDPAY_REFUND_ERROR_RESPONSE);
        paymentServiceResponse(refundResponse);
    }

    public void mockAuthorisationGatewayError() {
        stubFor(
                post(urlPathEqualTo("/jsp/merchant/xml/paymentService.jsp"))
                        .willReturn(
                                aResponse().withStatus(404)
                        )
        );
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
}
