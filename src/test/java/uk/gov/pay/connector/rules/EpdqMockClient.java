package uk.gov.pay.connector.rules;

import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.TEXT_XML;
import static uk.gov.pay.connector.gateway.epdq.EpdqPaymentProvider.ROUTE_FOR_MAINTENANCE_ORDER;
import static uk.gov.pay.connector.gateway.epdq.EpdqPaymentProvider.ROUTE_FOR_NEW_ORDER;
import static uk.gov.pay.connector.gateway.epdq.EpdqPaymentProvider.ROUTE_FOR_QUERY_ORDER;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.*;

public class EpdqMockClient {

    public EpdqMockClient() {
    }

    public void mockAuthorisationSuccess() {
        paymentServiceResponse(ROUTE_FOR_NEW_ORDER, TestTemplateResourceLoader.load(EPDQ_AUTHORISATION_SUCCESS_RESPONSE));
    }

    public void mockAuthorisationQuerySuccess() {
        paymentServiceResponse(ROUTE_FOR_QUERY_ORDER, TestTemplateResourceLoader.load(EPDQ_AUTHORISATION_SUCCESS_RESPONSE));
    }

    public void mockAuthorisation3dsSuccess() {
        paymentServiceResponse(ROUTE_FOR_NEW_ORDER, TestTemplateResourceLoader.load(EPDQ_AUTHORISATION_SUCCESS_3D_RESPONSE));
    }

    public void mockAuthorisationFailure() {
        paymentServiceResponse(ROUTE_FOR_NEW_ORDER, TestTemplateResourceLoader.load(EPDQ_AUTHORISATION_FAILED_RESPONSE));
    }

    public void mockAuthorisationError() {
        paymentServiceResponse(ROUTE_FOR_NEW_ORDER, TestTemplateResourceLoader.load(EPDQ_AUTHORISATION_ERROR_RESPONSE));
    }

    public void mockAuthorisationWaitingExternal() {
        paymentServiceResponse(ROUTE_FOR_NEW_ORDER, TestTemplateResourceLoader.load(EPDQ_AUTHORISATION_WAITING_EXTERNAL_RESPONSE));
    }

    public void mockAuthorisationWaiting() {
        paymentServiceResponse(ROUTE_FOR_NEW_ORDER, TestTemplateResourceLoader.load(EPDQ_AUTHORISATION_WAITING_RESPONSE));
    }

    public void mockAuthorisationOther() {
        paymentServiceResponse(ROUTE_FOR_NEW_ORDER, TestTemplateResourceLoader.load(EPDQ_AUTHORISATION_OTHER_RESPONSE));
    }

    public void mockCaptureSuccess() {
        paymentServiceResponse(ROUTE_FOR_MAINTENANCE_ORDER, TestTemplateResourceLoader.load(EPDQ_CAPTURE_SUCCESS_RESPONSE));
    }

    public void mockCaptureError() {
        paymentServiceResponse(ROUTE_FOR_MAINTENANCE_ORDER, TestTemplateResourceLoader.load(EPDQ_CAPTURE_ERROR_RESPONSE));
    }

    public void mockCancelSuccess() {
        paymentServiceResponse(ROUTE_FOR_MAINTENANCE_ORDER, TestTemplateResourceLoader.load(EPDQ_CANCEL_SUCCESS_RESPONSE));
    }

    public void mockRefundSuccess() {
        paymentServiceResponse(ROUTE_FOR_MAINTENANCE_ORDER, TestTemplateResourceLoader.load(EPDQ_REFUND_SUCCESS_RESPONSE));
    }

    public void mockRefundError() {
        paymentServiceResponse(ROUTE_FOR_MAINTENANCE_ORDER, TestTemplateResourceLoader.load(EPDQ_REFUND_ERROR_RESPONSE));
    }

    private void paymentServiceResponse(String route, String responseBody) {
        //FIXME - This mocking approach is very poor. Needs to be revisited. Story PP-900 created.
        stubFor(
                post(urlPathEqualTo(String.format("/epdq/%s", route)))
                        .willReturn(
                                aResponse()
                                        .withHeader(CONTENT_TYPE, TEXT_XML)
                                        .withStatus(200)
                                        .withBody(responseBody)
                        )
        );
    }
}
