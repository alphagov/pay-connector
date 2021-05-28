package uk.gov.pay.connector.it.dao;

import org.junit.Before;
import org.junit.Test;
import uk.gov.service.payments.commons.model.CardExpiryDate;
import uk.gov.pay.connector.cardtype.model.domain.CardType;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.AddressEntity;
import uk.gov.pay.connector.charge.model.CardDetailsEntity;
import uk.gov.pay.connector.charge.model.FirstDigitsCardNumber;
import uk.gov.pay.connector.charge.model.LastDigitsCardNumber;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.model.domain.AddressFixture;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.apache.commons.lang.math.RandomUtils.nextLong;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.util.RandomIdGenerator.randomUuid;


public class ChargeDaoCardDetailsIT extends DaoITestBase {

    private ChargeDao chargeDao;
    private GatewayAccountDao gatewayAccountDao;

    @Before
    public void setUp() {
        chargeDao = env.getInstance(ChargeDao.class);
        gatewayAccountDao = env.getInstance(GatewayAccountDao.class);
    }

    private void createChargeWithIdAndDetails(long chargeId, DatabaseFixtures.TestCardDetails testCardDetails) {
        DatabaseFixtures.TestAccount testAccountFixture = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .insert();
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withChargeId(chargeId)
                .withCardDetails(testCardDetails)
                .withTestAccount(testAccountFixture)
                .withPaymentProvider(testAccountFixture.getPaymentProvider())
                .withChargeStatus(ChargeStatus.CAPTURE_SUBMITTED)
                .insert();
    }

    private DatabaseFixtures.TestCardDetails createCardDetailsForChargeWithId(long chargeId) {
        DatabaseFixtures.TestCardDetails testCardDetails = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCardDetails()
                .withChargeId(chargeId);
        createChargeWithIdAndDetails(chargeId, testCardDetails);
        return testCardDetails;
    }

    @Test
    public void findById_shouldFindCardDetails() {
        long chargeId = nextLong();
        DatabaseFixtures.TestCardDetails testCardDetails = createCardDetailsForChargeWithId(chargeId);
        Optional<ChargeEntity> chargeDaoOptional = chargeDao.findById(chargeId);


        assertThat(chargeDaoOptional.isPresent(), is(true));

        CardDetailsEntity cardDetailsEntity = chargeDaoOptional.get().getCardDetails();

        assertThat(cardDetailsEntity.getCardHolderName(), is(testCardDetails.getCardHolderName()));
        assertThat(cardDetailsEntity.getLastDigitsCardNumber(), is(testCardDetails.getLastDigitsCardNumber()));
        assertThat(cardDetailsEntity.getFirstDigitsCardNumber(), is(testCardDetails.getFirstDigitsCardNumber()));
        assertThat(cardDetailsEntity.getExpiryDate(), is(testCardDetails.getExpiryDate()));
        assertThat(cardDetailsEntity.getBillingAddress().isPresent(), is(true));
        assertThat(cardDetailsEntity.getBillingAddress().get().getLine1(), is(testCardDetails.getBillingAddress().getLine1()));
        assertThat(cardDetailsEntity.getBillingAddress().get().getLine2(), is(testCardDetails.getBillingAddress().getLine2()));
        assertThat(cardDetailsEntity.getBillingAddress().get().getPostcode(), is(testCardDetails.getBillingAddress().getPostcode()));
        assertThat(cardDetailsEntity.getBillingAddress().get().getCity(), is(testCardDetails.getBillingAddress().getCity()));
        assertThat(cardDetailsEntity.getBillingAddress().get().getCounty(), is(testCardDetails.getBillingAddress().getCounty()));
        assertThat(cardDetailsEntity.getBillingAddress().get().getCountry(), is(testCardDetails.getBillingAddress().getCountry()));
    }

    @Test
    public void findById_shouldFindCardDetailsIfCardDigitsAreNotPresent() {
        long chargeId = nextLong();
        DatabaseFixtures.TestCardDetails testCardDetails = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCardDetails()
                .withFirstDigitsOfCardNumber(null)
                .withLastDigitsOfCardNumber(null)
                .withChargeId(chargeId);
        createChargeWithIdAndDetails(chargeId, testCardDetails);
        Optional<ChargeEntity> chargeDaoOptional = chargeDao.findById(chargeId);
        assertThat(chargeDaoOptional.isPresent(), is(true));

        CardDetailsEntity cardDetailsEntity = chargeDaoOptional.get().getCardDetails();

        assertThat(cardDetailsEntity.getCardHolderName(), is(testCardDetails.getCardHolderName()));
        assertThat(cardDetailsEntity.getLastDigitsCardNumber(), is(nullValue()));
        assertThat(cardDetailsEntity.getFirstDigitsCardNumber(), is(nullValue()));
        assertThat(cardDetailsEntity.getExpiryDate(), is(testCardDetails.getExpiryDate()));
        assertThat(cardDetailsEntity.getBillingAddress().isPresent(), is(true));
        assertThat(cardDetailsEntity.getBillingAddress().get().getLine1(), is(testCardDetails.getBillingAddress().getLine1()));
        assertThat(cardDetailsEntity.getBillingAddress().get().getLine2(), is(testCardDetails.getBillingAddress().getLine2()));
        assertThat(cardDetailsEntity.getBillingAddress().get().getPostcode(), is(testCardDetails.getBillingAddress().getPostcode()));
        assertThat(cardDetailsEntity.getBillingAddress().get().getCity(), is(testCardDetails.getBillingAddress().getCity()));
        assertThat(cardDetailsEntity.getBillingAddress().get().getCounty(), is(testCardDetails.getBillingAddress().getCounty()));
        assertThat(cardDetailsEntity.getBillingAddress().get().getCountry(), is(testCardDetails.getBillingAddress().getCountry()));
    }

    @Test
    public void persist_shouldStoreCardDetails() {
        GatewayAccountEntity testAccount = new GatewayAccountEntity("sandbox", new HashMap<>(), GatewayAccountType.TEST);
        testAccount.setExternalId(randomUuid());
        gatewayAccountDao.persist(testAccount);

        Address billingAddress = AddressFixture.anAddress().build();
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        CardDetailsEntity cardDetailsEntity = new CardDetailsEntity(FirstDigitsCardNumber.of("123456"), LastDigitsCardNumber.of("1258"),
                "Mr. Pay Mc Payment", CardExpiryDate.valueOf("03/09"), "VISA", CardType.DEBIT, new AddressEntity(billingAddress));
        chargeEntity.setCardDetails(cardDetailsEntity);
        chargeDao.persist(chargeEntity);

        Map<String, Object> cardDetailsSaved = databaseTestHelper.getChargeCardDetails(chargeEntity.getId());
        assertThat(cardDetailsSaved, hasEntry("card_type", "DEBIT"));
    }

    @Test
    public void persist_shouldStoreNullCardTypeDetails() {
        GatewayAccountEntity testAccount = new GatewayAccountEntity("sandbox", new HashMap<>(), GatewayAccountType.TEST);
        testAccount.setExternalId(randomUuid());
        gatewayAccountDao.persist(testAccount);

        Address billingAddress = AddressFixture.anAddress().build();
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        CardDetailsEntity cardDetailsEntity = new CardDetailsEntity(FirstDigitsCardNumber.of("123456"), LastDigitsCardNumber.of("1258"),
                "Mr. Pay Mc Payment", CardExpiryDate.valueOf("03/09"), "VISA", null, new AddressEntity(billingAddress));
        chargeEntity.setCardDetails(cardDetailsEntity);
        chargeDao.persist(chargeEntity);

        Map<String, Object> cardDetailsSaved = databaseTestHelper.getChargeCardDetails(chargeEntity.getId());
        assertThat(cardDetailsSaved, hasEntry("card_type",null));
    }
}
