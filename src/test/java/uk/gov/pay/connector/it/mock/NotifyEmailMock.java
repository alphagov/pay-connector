package uk.gov.pay.connector.it.mock;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

public class NotifyEmailMock {

    public void responseWithEmailRequestResponse(int statusCode, String payload, int timeout) {
        ResponseDefinitionBuilder responseDefBuilder = aResponse()
                .withHeader(CONTENT_TYPE, APPLICATION_JSON)
                .withStatus(statusCode)
                .withBody(payload);

        if (timeout >= 0) {
            responseDefBuilder.withFixedDelay(timeout);
        }

        stubFor(
                post(urlPathEqualTo("/notifications/email"))
                        .withRequestBody(
                                matching(".*to*.*template.*")
                        )
                        .willReturn(
                                responseDefBuilder
                        )
        );
    }

    public void responseWithEmailCheckStatusResponse(int statusCode, String payload, int timeout) {
        ResponseDefinitionBuilder responseDefBuilder = aResponse()
                .withHeader(CONTENT_TYPE, APPLICATION_JSON)
                .withStatus(statusCode)
                .withBody(payload);

        if (timeout >= 0) {
            responseDefBuilder.withFixedDelay(timeout);
        }

        stubFor(
                get(urlMatching("/notifications/.*"))
                        .willReturn(
                                responseDefBuilder
                        )
        );
    }
}
