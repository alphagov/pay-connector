package uk.gov.pay.connector.it.dao;

import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.dao.CardTypeDao;
import uk.gov.pay.connector.model.domain.CardTypeEntity;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;

public class CardTypeDaoJpaITest extends DaoITestBase {

    private CardTypeDao cardTypeDao;

    private DatabaseFixtures.TestCardType masterCardCreditCardTypeTestRecord;
    private DatabaseFixtures.TestCardType masterCardDebitCardTypeTestRecord;

    @Before
    public void setUp() throws Exception {
        cardTypeDao = env.getInstance(CardTypeDao.class);

        databaseTestHelper.deleteAllCardTypes();

        DatabaseFixtures.TestCardType masterCardCreditCardType = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aMastercardCreditCardType();
        DatabaseFixtures.TestCardType masterCardDebitCardType = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aMastercardDebitCardType();
        DatabaseFixtures.TestCardType visaCardDebitCardType = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aVisaDebitCardType();

        masterCardCreditCardTypeTestRecord = masterCardCreditCardType.insert();
        masterCardDebitCardTypeTestRecord = masterCardDebitCardType.insert();
        visaCardDebitCardType.insert();
    }

    @Test
    public void findByBrand_shouldFindCardTypes() {
        List<CardTypeEntity> cardTypes = cardTypeDao.findByBrand(masterCardCreditCardTypeTestRecord.getBrand());

        assertThat(cardTypes.size(), is(2));

        CardTypeEntity firstCardType = cardTypes.get(0);
        assertNotNull(firstCardType.getId());
        assertThat(firstCardType.getBrand(), is(masterCardCreditCardTypeTestRecord.getBrand()));
        assertThat(firstCardType.getType(), is(masterCardCreditCardTypeTestRecord.getType()));
        assertThat(firstCardType.getLabel(), is(masterCardCreditCardTypeTestRecord.getLabel()));
        assertNotNull(firstCardType.getVersion());

        CardTypeEntity secondCardType = cardTypes.get(1);
        assertNotNull(secondCardType.getId());
        assertThat(secondCardType.getBrand(), is(masterCardDebitCardTypeTestRecord.getBrand()));
        assertThat(secondCardType.getType(), is(masterCardDebitCardTypeTestRecord.getType()));
        assertThat(secondCardType.getLabel(), is(masterCardDebitCardTypeTestRecord.getLabel()));
        assertNotNull(secondCardType.getVersion());
    }

    @Test
    public void findByBrand_shouldNotFindCardType() {
        String noExistingExternalId = "unknown-card-brand";
        assertThat(cardTypeDao.findByBrand(noExistingExternalId).size(), is(0));
    }
}
