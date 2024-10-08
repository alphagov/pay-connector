package uk.gov.pay.connector.rules;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingXPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.HttpHeaders.COOKIE;
import static javax.ws.rs.core.HttpHeaders.SET_COOKIE;
import static javax.ws.rs.core.MediaType.TEXT_XML;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_3DS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_AUTHORISATION_CREATE_TOKEN_SUCCESS_RESPONSE_WITH_TRANSACTION_IDENTIFIER;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_AUTHORISATION_FAILED_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_AUTHORISATION_FAILED_USER_NON_PRESENT_NON_RETRIABLE_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_AUTHORISATION_PARES_PARSE_ERROR_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_CANCEL_ERROR_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_CANCEL_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_CAPTURED_INQUIRY_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_CAPTURE_ERROR_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_CAPTURE_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_EXEMPTION_REQUEST_SOFT_DECLINE_RESULT_REJECTED_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_INQUIRY_CREDENTIAL_VALIDATION_INVALID_MERCHANT_ID_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_INQUIRY_CREDENTIAL_VALIDATION_UNEXPECTED_ERROR_CODE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_INQUIRY_CREDENTIAL_VALIDATION_VALID_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_REFUND_ERROR_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_REFUND_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

public class WorldpayMockClient {

    public static final String WORLDPAY_URL = "/jsp/merchant/xml/paymentService.jsp";
    private WireMockServer wireMockServer;

    public WorldpayMockClient(WireMockServer wireMockServer) {
        this.wireMockServer = wireMockServer;
    }

    public void mockAuthorisationSuccess() {
        String authoriseResponse = load(WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE);
        paymentServiceResponse(authoriseResponse);
    }

    public void mockAuthorisationRequires3ds() {
        String authorise3dsResponse = load(WORLDPAY_3DS_RESPONSE);
        paymentServiceResponse(authorise3dsResponse);
    }

    public void mockAuthorisationRequires3dsWithMachineCookie(String cookie) {
        String authorise3dsResponse = load(WORLDPAY_3DS_RESPONSE);
        paymentServiceResponseWithMachineCookie(authorise3dsResponse, cookie);
    }

    public void mockAuthorisationSuccess3dsMatchingOnMachineCookie(String cookie) {
        String authorise3dsResponse = load(WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE);
        paymentServiceResponseStubWithMatchingCookieHeader(authorise3dsResponse, cookie);
    }

    public void mockAuthorisationSuccessWithRecurringPaymentToken() {
        String authoriseResponse = load(WORLDPAY_AUTHORISATION_CREATE_TOKEN_SUCCESS_RESPONSE_WITH_TRANSACTION_IDENTIFIER);
        paymentServiceResponse(authoriseResponse);
    }
    
    public void mockAuthorisationFailure() {
        String authoriseResponse = load(WORLDPAY_AUTHORISATION_FAILED_RESPONSE);
        paymentServiceResponse(authoriseResponse);
    }

    public void mockAuthorisationFailureUserNotPresentNonRetriablePayment() {
        String authoriseResponse = load(WORLDPAY_AUTHORISATION_FAILED_USER_NON_PRESENT_NON_RETRIABLE_RESPONSE);
        paymentServiceResponse(authoriseResponse);
    }

    public void mockAuthorisationPaResParseError() {
        String authoriseResponse = load(WORLDPAY_AUTHORISATION_PARES_PARSE_ERROR_RESPONSE);
        paymentServiceResponse(authoriseResponse);
    }

    public void mockCaptureSuccess() {
        String captureResponse = load(WORLDPAY_CAPTURE_SUCCESS_RESPONSE);
        paymentServiceResponse(captureResponse);
    }

    public void mockCaptureError() {
        String captureResponse = load(WORLDPAY_CAPTURE_ERROR_RESPONSE);
        paymentServiceResponse(captureResponse);
    }

    public void mockCancelSuccess() {
        String cancelResponse = load(WORLDPAY_CANCEL_SUCCESS_RESPONSE);
        paymentServiceResponse(cancelResponse);
    }

    public void mockCancelError() {
        String cancelResponse = load(WORLDPAY_CANCEL_ERROR_RESPONSE);
        paymentServiceResponse(cancelResponse);
    }

    public void mockCancelSuccessOnlyFor(String gatewayTransactionId) {
        String cancelSuccessResponse = load(WORLDPAY_CANCEL_SUCCESS_RESPONSE);
        String bodyMatchXpath = "//orderModification[@orderCode = '" + gatewayTransactionId + "']";
        bodyMatchingPaymentServiceResponse(bodyMatchXpath, cancelSuccessResponse);

    }

    public void mockRefundSuccess() {
        String refundResponse = load(WORLDPAY_REFUND_SUCCESS_RESPONSE);
        paymentServiceResponse(refundResponse);
    }

    public void mockRefundError() {
        String refundResponse = load(WORLDPAY_REFUND_ERROR_RESPONSE);
        paymentServiceResponse(refundResponse);
    }
    
    public void mockInquiryCaptured() {
        String capturedResponse = load(WORLDPAY_CAPTURED_INQUIRY_RESPONSE);
        paymentServiceResponse(capturedResponse);
    }
    
    public void mockCredentialsValidationValid() {
        String credentialsValidResponse = load(WORLDPAY_INQUIRY_CREDENTIAL_VALIDATION_VALID_RESPONSE);
        paymentServiceResponse(credentialsValidResponse);
    }

    public void mockCredentialsValidationInvalid() {
        String credentialsValidResponse = load(WORLDPAY_INQUIRY_CREDENTIAL_VALIDATION_INVALID_MERCHANT_ID_RESPONSE);
        paymentServiceResponse(credentialsValidResponse);
    }

    public void mockCredentialsValidationUnexpectedResponse() {
        String credentialsValidResponse = load(WORLDPAY_INQUIRY_CREDENTIAL_VALIDATION_UNEXPECTED_ERROR_CODE);
        paymentServiceResponse(credentialsValidResponse);
    }

    public void mockAuthorisationGatewayError() {
        wireMockServer.stubFor(
                post(urlPathEqualTo(WORLDPAY_URL))
                        .willReturn(
                                aResponse().withStatus(404)
                        )
        );
    }

    public void mockAuthorisationQuerySuccess() {
        String authSuccessResponse = load(WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE);
        String bodyMatchXpath = "//orderInquiry[@orderCode]";
        bodyMatchingPaymentServiceResponse(bodyMatchXpath, authSuccessResponse);    
    }

    public void mockServerFault() {
        wireMockServer.stubFor(
                post(urlPathEqualTo(WORLDPAY_URL))
                        .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))
        );
    }

    private void paymentServiceResponse(String responseBody) {
        //FIXME - This mocking approach is very poor. Needs to be revisited. Story PP-900 created.
        wireMockServer.stubFor(
                post(urlPathEqualTo(WORLDPAY_URL))
                        .willReturn(
                                aResponse()
                                        .withHeader(CONTENT_TYPE, TEXT_XML)
                                        .withStatus(200)
                                        .withBody(responseBody)
                        )
        );
    }

    private void paymentServiceResponseStubWithMatchingCookieHeader(String responseBody, String cookie) {
        wireMockServer.stubFor(
                post(urlPathEqualTo(WORLDPAY_URL))
                        .withHeader(COOKIE, matching("machine=" + cookie))
                        .willReturn(
                                aResponse()
                                        .withHeader(CONTENT_TYPE, TEXT_XML)
                                        .withStatus(200)
                                        .withBody(responseBody)
                        )
        );
    }

    private void paymentServiceResponseWithMachineCookie(String responseBody, String cookie) {
        wireMockServer.stubFor(
                post(urlPathEqualTo(WORLDPAY_URL))
                        .willReturn(
                                aResponse()
                                        .withHeader(CONTENT_TYPE, TEXT_XML)
                                        .withHeader(SET_COOKIE, "machine=" + cookie)
                                        .withStatus(200)
                                        .withBody(responseBody)
                        )
        );
    }

    private void bodyMatchingPaymentServiceResponse(String xpathContent, String responseBody) {
        wireMockServer.stubFor(
                post(urlPathEqualTo(WORLDPAY_URL))
                        .withRequestBody(matchingXPath(xpathContent))
                        .willReturn(
                                aResponse()
                                        .withHeader(CONTENT_TYPE, TEXT_XML)
                                        .withStatus(200)
                                        .withBody(responseBody)
                        )
        );
    }

    public void mockResponsesForExemptionEngineSoftDecline() {
        wireMockServer.stubFor(post(urlEqualTo(WORLDPAY_URL)).inScenario("Exemption Engine soft decline")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(200).withBody(load(WORLDPAY_EXEMPTION_REQUEST_SOFT_DECLINE_RESULT_REJECTED_RESPONSE)))
                .willSetStateTo("Soft decline triggered"));

        wireMockServer.stubFor(post(urlEqualTo(WORLDPAY_URL)).inScenario("Exemption Engine soft decline")
                .whenScenarioStateIs("Soft decline triggered")
                .willReturn(aResponse().withStatus(200).withBody(load(WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE))));
    }
}
