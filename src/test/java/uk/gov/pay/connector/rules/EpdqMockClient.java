package uk.gov.pay.connector.rules;

import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.TEXT_XML;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.*;

public class EpdqMockClient {

    public EpdqMockClient() {
    }

    public void mockAuthorisationSuccess() {
        paymentServiceResponse(TestTemplateResourceLoader.load(EPDQ_AUTHORISATION_SUCCESS_RESPONSE));
    }

    public void mockAuthorisationFailure() {
        paymentServiceResponse(TestTemplateResourceLoader.load(EPDQ_AUTHORISATION_FAILED_RESPONSE));
    }

    public void mockAuthorisationError() {
        paymentServiceResponse(TestTemplateResourceLoader.load(EPDQ_AUTHORISATION_ERROR_RESPONSE));
    }

    public void mockAuthorisationWaitingExternal() {
        paymentServiceResponse(TestTemplateResourceLoader.load(EPDQ_AUTHORISATION_WAITING_EXTERNAL_RESPONSE));
    }

    public void mockAuthorisationWaiting() {
        paymentServiceResponse(TestTemplateResourceLoader.load(EPDQ_AUTHORISATION_WAITING_RESPONSE));
    }

    public void mockAuthorisationOther() {
        paymentServiceResponse(TestTemplateResourceLoader.load(EPDQ_AUTHORISATION_OTHER_RESPONSE));
    }

    private void paymentServiceResponse(String responseBody) {
        //FIXME - This mocking approach is very poor. Needs to be revisited. Story PP-900 created.
        stubFor(
                post(urlPathEqualTo("/epdq/orderdirect.asp"))
                        .willReturn(
                                aResponse()
                                        .withHeader(CONTENT_TYPE, TEXT_XML)
                                        .withStatus(200)
                                        .withBody(responseBody)
                        )
        );
    }
}
