package uk.gov.pay.connector.it.dao;

import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.dao.CardTypeDao;
import uk.gov.pay.connector.model.domain.CardTypeEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class CardTypeDaoJpaITest extends DaoITestBase {

    private CardTypeDao cardTypeDao;

    private DatabaseFixtures.TestCardType mastercardCreditCardTypeTestRecord;
    private DatabaseFixtures.TestCardType visaDebitCardTypeTestRecord;

    @Before
    public void setUp() throws Exception {
        cardTypeDao = env.getInstance(CardTypeDao.class);

        databaseTestHelper.deleteAllCardTypes();

        this.mastercardCreditCardTypeTestRecord = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aMastercardCreditCardType()
                .insert();

        this.visaDebitCardTypeTestRecord = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aVisaDebitCardType()
                .insert();
    }

    @Test
    public void findById_shouldFindCardType() {
        Optional<CardTypeEntity> cardTypeOptional = cardTypeDao.findById(mastercardCreditCardTypeTestRecord.getId());

        assertThat(cardTypeOptional.isPresent(), is(true));

        CardTypeEntity cardTypeEntity = cardTypeOptional.get();

        assertThat(cardTypeEntity.getId(), is(notNullValue()));
        assertThat(cardTypeEntity.getLabel(), is(mastercardCreditCardTypeTestRecord.getLabel()));
        assertThat(cardTypeEntity.getType(), is(mastercardCreditCardTypeTestRecord.getType()));
        assertThat(cardTypeEntity.getBrand(), is(mastercardCreditCardTypeTestRecord.getBrand()));
    }

    @Test
    public void findById_shouldNotFindCardType() {
        UUID noExistingChargeId = UUID.randomUUID();
        assertThat(cardTypeDao.findById(noExistingChargeId).isPresent(), is(false));
    }

    @Test
    public void findAll_shouldFindAllCardTypes() {
        List<CardTypeEntity> cardTypeEntities = cardTypeDao.findAll();

        assertThat(cardTypeEntities, containsInAnyOrder(
                allOf(
                        hasProperty("id", is(Matchers.notNullValue())),
                        hasProperty("label", is(mastercardCreditCardTypeTestRecord.getLabel())),
                        hasProperty("type", is(mastercardCreditCardTypeTestRecord.getType())),
                        hasProperty("brand", is(mastercardCreditCardTypeTestRecord.getBrand()))
                ), allOf(
                        hasProperty("id", is(Matchers.notNullValue())),
                        hasProperty("label", is(visaDebitCardTypeTestRecord.getLabel())),
                        hasProperty("type", is(visaDebitCardTypeTestRecord.getType())),
                        hasProperty("brand", is(visaDebitCardTypeTestRecord.getBrand()))
                )));
    }
}
