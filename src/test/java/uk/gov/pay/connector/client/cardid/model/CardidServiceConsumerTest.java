package uk.gov.pay.connector.client.cardid.model;

import au.com.dius.pact.consumer.PactVerification;
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
import uk.gov.service.payments.commons.testing.pact.consumers.PactProviderRule;
import uk.gov.service.payments.commons.testing.pact.consumers.Pacts;

import javax.ws.rs.client.Client;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CardidServiceConsumerTest {
    
    @Rule
    public PactProviderRule cardidRule = new PactProviderRule("cardid", this);
    
    @Mock
    ConnectorConfiguration configuration;
    
    private CardidService cardidService;

    @Before
    public void setUp() throws Exception {
        when(configuration.getCardidBaseUrl()).thenReturn(cardidRule.getUrl());
        Client client = RestClientFactory.buildClient(new RestClientConfig(), null);
        cardidService = new CardidService(client, configuration);
    }

    @Test
    @PactVerification("cardid")
    @Pacts(pacts = {"connector-cardid-get-card-information-found"})
    public void getCardInformation_shouldDeserialiseCardFoundResponse() {
        String cardNumber = "2221000000000000";
        Optional<CardInformation> maybeCardInformation = cardidService.getCardInformation(cardNumber);
        
        assertThat(maybeCardInformation.isPresent(), is(true));
        CardInformation cardInformation = maybeCardInformation.get();
        assertThat(cardInformation.type(), is(CardidCardType.CREDIT));
        assertThat(cardInformation.brand(), is("master-card"));
        assertThat(cardInformation.label(), is ("MC"));
        assertThat(cardInformation.prepaidStatus(), is(PayersCardPrepaidStatus.NOT_PREPAID));
        assertThat(cardInformation.isCorporate(), is(false));
    }

    @Test
    @PactVerification("cardid")
    @Pacts(pacts = {"connector-cardid-get-card-information-not-found"})
    public void getCardInformation_shouldReturnEmpryOptionalWhenCardidReturns404() {
        String cardNumber = "1000000000000000";
        assertThat(cardidService.getCardInformation(cardNumber), is(Optional.empty()));
    }
}
