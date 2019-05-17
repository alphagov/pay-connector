package uk.gov.pay.connector.it.dao;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.cardtype.dao.CardTypeDao;
import uk.gov.pay.connector.cardtype.model.domain.CardTypeEntity;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.junit.DropwizardTestContext;
import uk.gov.pay.connector.junit.TestContext;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static uk.gov.pay.connector.cardtype.model.domain.CardTypeEntity.SupportedType.DEBIT;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class SupportedTypeDaoJpaIT {

    private CardTypeDao cardTypeDao;

    @DropwizardTestContext
    protected TestContext testContext;

    @Before
    public void setup() {
        cardTypeDao = testContext.getInstanceFromGuiceContainer(CardTypeDao.class);
    }

    @Test
    public void findByBrand_shouldFindCardTypes() {
        List<CardTypeEntity> cardTypes = cardTypeDao.findByBrand("master-card");

        assertThat(cardTypes.size(), is(2));

        Optional<CardTypeEntity> maybeCreditCard =
                cardTypes.stream().filter(card -> card.getType().equals(CardTypeEntity.SupportedType.CREDIT)).findFirst();
        assertThat(maybeCreditCard.isPresent(), is(true));
        CardTypeEntity creditCard = maybeCreditCard.get();
        assertNotNull(creditCard.getId());
        assertThat(creditCard.getBrand(), is("master-card"));
        assertThat(creditCard.getType().toString(), is(CardTypeEntity.SupportedType.CREDIT.toString()));
        assertThat(creditCard.getLabel(), is("Mastercard"));
        assertNotNull(creditCard.getVersion());

        Optional<CardTypeEntity> maybeDebitCard =
                cardTypes.stream().filter(card -> card.getType().equals(CardTypeEntity.SupportedType.DEBIT)).findFirst();
        assertThat(maybeDebitCard.isPresent(), is(true));
        CardTypeEntity debitCard = maybeDebitCard.get();
        assertNotNull(debitCard.getId());
        assertNotNull(debitCard.getId());
        assertThat(debitCard.getBrand(), is("master-card"));
        assertThat(debitCard.getType().toString(), is(CardTypeEntity.SupportedType.DEBIT.toString()));
        assertThat(debitCard.getLabel(), is("Mastercard"));
        assertNotNull(debitCard.getVersion());
    }

    @Test
    public void findByBrand_shouldNotFindCardType() {
        String noExistingExternalId = "unknown-card-brand";
        assertThat(cardTypeDao.findByBrand(noExistingExternalId).size(), is(0));
    }

    @Test
    public void findAllNon3ds_shouldFindCardTypes() {

        List<CardTypeEntity> cardTypes = cardTypeDao.findAllNon3ds();

        assertThat(cardTypes.size(), is(9));
        final Optional<CardTypeEntity> maybe3DSCard = cardTypes.stream().filter(CardTypeEntity::isRequires3ds).findFirst();
        assertThat(maybe3DSCard.isPresent(), is(false));
    }

    @Test
    public void findAll_shouldFindCardTypes_includingTypesRequiring3ds() {

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
