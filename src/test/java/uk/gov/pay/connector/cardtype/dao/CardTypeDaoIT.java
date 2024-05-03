package uk.gov.pay.connector.cardtype.dao;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.cardtype.model.domain.CardType;
import uk.gov.pay.connector.cardtype.model.domain.CardTypeEntity;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.it.base.ITestBaseExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isA;

public class CardTypeDaoIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();

    private static CardTypeDao cardTypeDao;

    @BeforeAll
    static void setup() {
        cardTypeDao = app.getInstanceFromGuiceContainer(CardTypeDao.class);
    }

    @Test
    void findById() {
        List<CardTypeEntity> allCardTypes = cardTypeDao.findAll();
        CardTypeEntity aCard = allCardTypes.get(0);
        Optional<CardTypeEntity> maybeCardTypeEntity = cardTypeDao.findById(aCard.getId());
        assertThat(maybeCardTypeEntity.isPresent(), is(true));
    }

    @Test
    void findAll() {
        List<CardTypeEntity> allCardTypes = cardTypeDao.findAll();
        assertThat(allCardTypes, hasSize(10));
        Optional<CardTypeEntity> maybeCard = allCardTypes.stream()
                .filter(c -> c.getLabel().equals("Mastercard") && c.getType().equals(CardType.DEBIT))
                .findFirst();
        assertThat(maybeCard.isPresent(), is(true));
        CardTypeEntity mastercardDebit = maybeCard.get();
        assertThat(mastercardDebit.getBrand(), is("master-card"));
        assertThat(mastercardDebit.getId(), isA(UUID.class));
    }

    @Test
    void findByBrand() {
        final List<CardTypeEntity> cards = cardTypeDao.findByBrand("master-card");
        assertThat(cards, hasSize(2));
    }

    @Test
    void findAllNon3ds() {
        final List<CardTypeEntity> allNon3ds = cardTypeDao.findAllNon3ds();
        assertThat(allNon3ds, hasSize(9));
    }
}
