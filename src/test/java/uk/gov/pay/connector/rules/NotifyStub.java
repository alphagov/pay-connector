package uk.gov.pay.connector.rules;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;


import java.util.Map;
import java.util.UUID;

public class NotifyStub {
    private final WireMockServer wireMockServer;
    
    public NotifyStub(WireMockServer wireMockServer) {
        this.wireMockServer = wireMockServer;
    }
    
    public StubMapping respondWithSuccess() {
        Map<String, Object> payload = Map.of(
                "id", "60b922b3-c41a-4e7e-8c12-efa7ea9ee7d2",
                "content", Map.of(
                        "body", "",
                        "subject", ""
                ),
                "template", Map.of(
                        "id", "c60e7068-27fc-4551-8ae9-2dbc3a9a080d",
                        "version", "1",
                        "uri", ""
                )
        );
        
        return wireMockServer.stubFor(
                post(urlPathEqualTo("/v2/notifications/email"))
                        .willReturn(
                                aResponse()
                                        .withHeader(CONTENT_TYPE, APPLICATION_JSON)
                                        .withStatus(201)
                                        .withBody(toJson(payload))
                        )
        );
    }
    
    public StubMapping respondWithFailure() {
        return wireMockServer.stubFor(
                post(urlPathEqualTo("/v2/notifications/email"))
                        .willReturn(
                                aResponse()
                                        .withHeader(CONTENT_TYPE, APPLICATION_JSON)
                                        .withStatus(400)
                                        .withBody(toJson(Map.of()))
                        )
        );
    }
}
