package uk.gov.pay.connector.cardtype.dao;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.cardtype.model.domain.CardTypeEntity;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.junit.DropwizardTestContext;
import uk.gov.pay.connector.junit.TestContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isA;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class CardTypeDaoIT {

    @DropwizardTestContext
    protected TestContext testContext;
    private CardTypeDao cardTypeDao;

    @Before
    public void setup() {
        cardTypeDao = testContext.getInstanceFromGuiceContainer(CardTypeDao.class);
    }

    @Test
    public void findById() {
        List<CardTypeEntity> allCardTypes = cardTypeDao.findAll();
        CardTypeEntity aCard = allCardTypes.get(0);
        Optional<CardTypeEntity> maybeCardTypeEntity = cardTypeDao.findById(aCard.getId());
        assertThat(maybeCardTypeEntity.isPresent(), is(true));
    }

    @Test
    public void findAll() {
        List<CardTypeEntity> allCardTypes = cardTypeDao.findAll();
        assertThat(allCardTypes, hasSize(10));
        Optional<CardTypeEntity> maybeCard = allCardTypes.stream()
                .filter(c -> c.getLabel().equals("Mastercard") && c.getType().equals(CardTypeEntity.SupportedType.DEBIT))
                .findFirst();
        assertThat(maybeCard.isPresent(), is(true));
        CardTypeEntity mastercardDebit = maybeCard.get();
        assertThat(mastercardDebit.getBrand(), is("master-card"));
        assertThat(mastercardDebit.getId(), isA(UUID.class));
    }

    @Test
    public void findByBrand() {
        final List<CardTypeEntity> cards = cardTypeDao.findByBrand("master-card");
        assertThat(cards, hasSize(2));
    }

    @Test
    public void findAllNon3ds() {
        final List<CardTypeEntity> allNon3ds = cardTypeDao.findAllNon3ds();
        assertThat(allNon3ds, hasSize(9));
    }
}
