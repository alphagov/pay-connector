package uk.gov.pay.connector.client.cardid.model;

import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit.PactProviderRule;
import au.com.dius.pact.consumer.junit.PactVerification;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import jakarta.ws.rs.client.Client;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.RestClientFactory;
import uk.gov.pay.connector.app.config.RestClientConfig;
import uk.gov.pay.connector.client.cardid.service.CardidService;
import uk.gov.pay.connector.gateway.model.PayersCardPrepaidStatus;

import java.util.Map;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CardidServiceConsumerTest {

    private static final String FOUND_CARD_NUMBER = "2221000000000000";
    private static final String NOT_FOUND_CARD_NUMBER = "1000000000000000";

    @Rule
    public PactProviderRule mockCardId = new PactProviderRule("cardid", this);

    @Mock
    private ConnectorConfiguration configuration;

    private CardidService cardidService;

    @Before
    public void setUp() {
        when(configuration.getCardidBaseUrl()).thenReturn(mockCardId.getUrl());
        Client client = RestClientFactory.buildClient(new RestClientConfig(), null);
        cardidService = new CardidService(client, configuration);
    }

    @Pact(consumer = "connector")
    public RequestResponsePact cardNumberFound(PactDslWithProvider builder) {
        return builder
                .uponReceiving("a get card information request when the card number is found in the BIN ranges")
                .path("/v1/api/card")
                .headers("Content-Type", "application/json")
                .method("POST")
                .body(new PactDslJsonBody().stringValue("cardNumber", FOUND_CARD_NUMBER))
                .willRespondWith()
                .status(200)
                .headers(Map.of("Content-Type", "application/json"))
                .body(new PactDslJsonBody()
                        .stringValue("brand", "master-card")
                        .stringValue("type", "C")
                        .stringValue("label", "MC")
                        .booleanValue("corporate", false)
                        .stringValue("prepaid", "NOT_PREPAID"))
                .toPact();
    }

    @Pact(consumer = "connector")
    public RequestResponsePact cardNumberNotFound(PactDslWithProvider builder) {
        return builder
                .uponReceiving("a get card information request when the card number is not found in the BIN ranges")
                .path("/v1/api/card")
                .headers("Content-Type", "application/json")
                .method("POST")
                .body(new PactDslJsonBody().stringValue("cardNumber", NOT_FOUND_CARD_NUMBER))
                .willRespondWith()
                .status(404)
                .headers(Map.of("Content-Type", "application/json"))
                .body(new PactDslJsonBody()
                        .stringValue("cardNumber", NOT_FOUND_CARD_NUMBER))
                .toPact();
    }


    @Test
    @PactVerification(fragment = "cardNumberFound")
    public void getCardInformation_shouldDeserialiseCardFoundResponse() {
        Optional<CardInformation> maybeCardInformation = cardidService.getCardInformation(FOUND_CARD_NUMBER);

        assertThat(maybeCardInformation.isPresent(), is(true));
        CardInformation cardInformation = maybeCardInformation.get();
        assertThat(cardInformation.type(), is(CardidCardType.CREDIT));
        assertThat(cardInformation.brand(), is("master-card"));
        assertThat(cardInformation.label(), is("MC"));
        assertThat(cardInformation.prepaidStatus(), is(PayersCardPrepaidStatus.NOT_PREPAID));
        assertThat(cardInformation.isCorporate(), is(false));
    }

    @Test
    @PactVerification(fragment = "cardNumberNotFound")
    public void getCardInformation_shouldReturnEmptyOptionalWhenCardidReturns404() {
        assertThat(cardidService.getCardInformation(NOT_FOUND_CARD_NUMBER), is(Optional.empty()));
    }
}
