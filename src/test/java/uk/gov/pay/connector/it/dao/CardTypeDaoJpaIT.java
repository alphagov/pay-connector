package uk.gov.pay.connector.it.dao;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.cardtype.dao.CardTypeDao;
import uk.gov.pay.connector.cardtype.model.domain.CardTypeEntity;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.it.base.ITestBaseExtension;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static uk.gov.pay.connector.cardtype.model.domain.CardType.CREDIT;
import static uk.gov.pay.connector.cardtype.model.domain.CardType.DEBIT;

public class CardTypeDaoJpaIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    private static CardTypeDao cardTypeDao;

    @BeforeAll
    public static void setup() {
        cardTypeDao = app.getInstanceFromGuiceContainer(CardTypeDao.class);
    }

    @Test
    void findByBrand_shouldFindCardTypes() {
        List<CardTypeEntity> cardTypes = cardTypeDao.findByBrand("master-card");

        assertThat(cardTypes.size(), is(2));

        Optional<CardTypeEntity> maybeCreditCard =
                cardTypes.stream().filter(card -> card.getType().equals(CREDIT)).findFirst();
        assertThat(maybeCreditCard.isPresent(), is(true));
        CardTypeEntity creditCard = maybeCreditCard.get();
        assertNotNull(creditCard.getId());
        assertThat(creditCard.getBrand(), is("master-card"));
        assertThat(creditCard.getType().toString(), is(CREDIT.toString()));
        assertThat(creditCard.getLabel(), is("Mastercard"));
        assertNotNull(creditCard.getVersion());

        Optional<CardTypeEntity> maybeDebitCard =
                cardTypes.stream().filter(card -> card.getType().equals(DEBIT)).findFirst();
        assertThat(maybeDebitCard.isPresent(), is(true));
        CardTypeEntity debitCard = maybeDebitCard.get();
        assertNotNull(debitCard.getId());
        assertNotNull(debitCard.getId());
        assertThat(debitCard.getBrand(), is("master-card"));
        assertThat(debitCard.getType().toString(), is(DEBIT.toString()));
        assertThat(debitCard.getLabel(), is("Mastercard"));
        assertNotNull(debitCard.getVersion());
    }

    @Test
    void findByBrand_shouldNotFindCardType() {
        String noExistingExternalId = "unknown-card-brand";
        assertThat(cardTypeDao.findByBrand(noExistingExternalId).size(), is(0));
    }

    @Test
    void findAllNon3ds_shouldFindCardTypes() {

        List<CardTypeEntity> cardTypes = cardTypeDao.findAllNon3ds();

        assertThat(cardTypes.size(), is(9));
        final Optional<CardTypeEntity> maybe3DSCard = cardTypes.stream().filter(CardTypeEntity::isRequires3ds).findFirst();
        assertThat(maybe3DSCard.isPresent(), is(false));
    }

    @Test
    void findAll_shouldFindCardTypes_includingTypesRequiring3ds() {

        List<CardTypeEntity> cardTypes = cardTypeDao.findAll();

        assertThat(cardTypes.size(), is(10));

        final Optional<CardTypeEntity> maybe3DSCard = cardTypes.stream().filter(CardTypeEntity::isRequires3ds).findFirst();
        assertThat(maybe3DSCard.isPresent(), is(true));
        CardTypeEntity cardWith3DS = maybe3DSCard.get();
        assertThat(cardWith3DS.getBrand(), is("maestro"));
        assertThat(cardWith3DS.getType(), is(DEBIT));
        assertThat(cardWith3DS.isRequires3ds(), is(true));
    }
}
