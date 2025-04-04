package uk.gov.pay.connector.rules;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import uk.gov.pay.connector.client.cardid.model.CardInformation;
import uk.gov.pay.connector.client.cardid.model.CardInformationRequest;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;

public class CardidStub {

    private static ObjectMapper objectMapper = new ObjectMapper();
    private final WireMockServer wireMockServer;

    public CardidStub(WireMockServer wireMockServer) {
        this.wireMockServer = wireMockServer;
    }

    public void returnCardInformation(String cardNumber, CardInformation cardInformation) throws JsonProcessingException {
        ResponseDefinitionBuilder response = aResponse().withHeader(CONTENT_TYPE, APPLICATION_JSON)
                .withStatus(200)
                .withBody(objectMapper.writeValueAsString(cardInformation));
        wireMockServer.stubFor(
                post(urlPathEqualTo("/v1/api/card"))
                        .withRequestBody(equalTo(objectMapper.writeValueAsString(new CardInformationRequest(cardNumber))))
                        .willReturn(response));
    }
    
    public void returnNotFound(String cardNumber) throws JsonProcessingException {
        ResponseDefinitionBuilder response = aResponse().withHeader(CONTENT_TYPE, TEXT_PLAIN)
                .withStatus(404);
        wireMockServer.stubFor(
                post(urlPathEqualTo("/v1/api/card"))
                        .withRequestBody(equalTo(objectMapper.writeValueAsString(new CardInformationRequest(cardNumber))))
                        .willReturn(response));
    }
}
