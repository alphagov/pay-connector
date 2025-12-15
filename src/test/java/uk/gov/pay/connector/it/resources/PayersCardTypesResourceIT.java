package uk.gov.pay.connector.it.resources;

import io.dropwizard.testing.junit.ResourceTestRule;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import uk.gov.pay.connector.cardtype.dao.CardTypeDao;
import uk.gov.pay.connector.cardtype.dao.CardTypeEntityBuilder;
import uk.gov.pay.connector.cardtype.model.domain.CardTypeEntity;
import uk.gov.pay.connector.cardtype.resource.CardTypesResource;

import jakarta.ws.rs.core.GenericType;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

public class PayersCardTypesResourceIT {
    private static final CardTypeDao mockedDao = mock(CardTypeDao.class);
    @ClassRule
    public static ResourceTestRule resources = ResourceTestRule.builder().addResource(new CardTypesResource(mockedDao)).build();
    private CardTypeEntity cardTypeEntity;

    @Before
    public void setUp() {
        cardTypeEntity = CardTypeEntityBuilder.aCardTypeEntity().build();
        when(mockedDao.findAll()).thenReturn(Collections.singletonList(cardTypeEntity));
    }

    @After
    public void tearDown() {
        reset(mockedDao);
    }

    @Test
    public void shouldGetAllCardTypesWhenCardTypesExist() {
        final Map<String, List<CardTypeEntity>> response = resources.target("/v1/api/card-types")
                .request()
                .get(new GenericType<Map<String, List<CardTypeEntity>>>() {
                });
        List<CardTypeEntity> cardTypeEntities = response.get("card_types");
        assertThat(cardTypeEntities, hasSize(1));
        CardTypeEntity visaCard = cardTypeEntities.getFirst();
        assertThat(visaCard, is(cardTypeEntity));
    }
}
