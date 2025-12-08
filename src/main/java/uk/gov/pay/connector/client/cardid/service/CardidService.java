package uk.gov.pay.connector.client.cardid.service;

import jakarta.inject.Inject;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.client.cardid.model.CardInformation;
import uk.gov.pay.connector.client.cardid.model.CardInformationRequest;

import java.util.Optional;

import static org.apache.http.HttpStatus.SC_OK;

public class CardidService {

    private static final String CARD_INFORMATION_PATH = "/v1/api/card";

    private final Client client;
    private final String cardidUrl;

    @Inject
    public CardidService(Client client, ConnectorConfiguration configuration) {
        this.client = client;
        this.cardidUrl = configuration.getCardidBaseUrl();
    }

    public Optional<CardInformation> getCardInformation(String cardNumber) {
        UriBuilder uri = UriBuilder.fromPath(cardidUrl).path(CARD_INFORMATION_PATH);
        var cardInformationRequest = new CardInformationRequest(cardNumber);
        Response response = client
                .target(uri)
                .request()
                .accept(MediaType.APPLICATION_JSON)
                .post(Entity.entity(cardInformationRequest, MediaType.APPLICATION_JSON));

        if (response.getStatus() == SC_OK) {
            return Optional.of(response.readEntity(CardInformation.class));
        }

        return Optional.empty();
    }
}
