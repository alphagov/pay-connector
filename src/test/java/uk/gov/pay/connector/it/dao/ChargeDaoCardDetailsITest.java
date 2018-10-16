package uk.gov.pay.connector.it.dao;

import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.commons.model.SupportedLanguage;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.model.FirstDigitsCardNumber;
import uk.gov.pay.connector.model.LastDigitsCardNumber;
import uk.gov.pay.connector.model.domain.Address;
import uk.gov.pay.connector.charge.model.AddressEntity;
import uk.gov.pay.connector.charge.model.CardDetailsEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static uk.gov.pay.connector.model.domain.AddressFixture.aValidAddress;


public class ChargeDaoCardDetailsITest extends DaoITestBase {

    private ChargeDao chargeDao;
    private GatewayAccountDao gatewayAccountDao;

    @Before
    public void setUp()  {
        chargeDao = env.getInstance(ChargeDao.class);
        gatewayAccountDao = env.getInstance(GatewayAccountDao.class);

    }

    private void createChargeWithIdAndDetails(long chargeId, DatabaseFixtures.TestCardDetails testCardDetails) {
        DatabaseFixtures.TestAccount testAccountFixture = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount();
        testAccountFixture.insert();
        DatabaseFixtures.TestCharge testCharge = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withChargeId(chargeId)
                .withCardDetails(testCardDetails)
                .withTestAccount(testAccountFixture)
                .withChargeStatus(ChargeStatus.CAPTURE_SUBMITTED);
        testCharge.insert();
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
        long chargeId = 123L;
        DatabaseFixtures.TestCardDetails testCardDetails = createCardDetailsForChargeWithId(chargeId);
        Optional<ChargeEntity> chargeDaoOptional = chargeDao.findById(chargeId);


        assertThat(chargeDaoOptional.isPresent(), is(true));

        CardDetailsEntity cardDetailsEntity = chargeDaoOptional.get().getCardDetails();

        assertThat(cardDetailsEntity.getCardHolderName(), is(testCardDetails.getCardHolderName()));
        assertThat(cardDetailsEntity.getLastDigitsCardNumber(), is(testCardDetails.getLastDigitsCardNumber()));
        assertThat(cardDetailsEntity.getFirstDigitsCardNumber(), is(testCardDetails.getFirstDigitsCardNumber()));
        assertThat(cardDetailsEntity.getExpiryDate(), is(testCardDetails.getExpiryDate()));
        assertNotNull(cardDetailsEntity.getBillingAddress());
        assertThat(cardDetailsEntity.getBillingAddress().getLine1(), is(testCardDetails.getBillingAddress().getLine1()));
        assertThat(cardDetailsEntity.getBillingAddress().getLine2(), is(testCardDetails.getBillingAddress().getLine2()));
        assertThat(cardDetailsEntity.getBillingAddress().getPostcode(), is(testCardDetails.getBillingAddress().getPostcode()));
        assertThat(cardDetailsEntity.getBillingAddress().getCity(), is(testCardDetails.getBillingAddress().getCity()));
        assertThat(cardDetailsEntity.getBillingAddress().getCounty(), is(testCardDetails.getBillingAddress().getCounty()));
        assertThat(cardDetailsEntity.getBillingAddress().getCountry(), is(testCardDetails.getBillingAddress().getCountry()));
    }

    @Test
    public void findById_shouldFindCardDetailsIfCardDigitsAreNotPresent() {
        long chargeId = 123L;
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
        assertNotNull(cardDetailsEntity.getBillingAddress());
        assertThat(cardDetailsEntity.getBillingAddress().getLine1(), is(testCardDetails.getBillingAddress().getLine1()));
        assertThat(cardDetailsEntity.getBillingAddress().getLine2(), is(testCardDetails.getBillingAddress().getLine2()));
        assertThat(cardDetailsEntity.getBillingAddress().getPostcode(), is(testCardDetails.getBillingAddress().getPostcode()));
        assertThat(cardDetailsEntity.getBillingAddress().getCity(), is(testCardDetails.getBillingAddress().getCity()));
        assertThat(cardDetailsEntity.getBillingAddress().getCounty(), is(testCardDetails.getBillingAddress().getCounty()));
        assertThat(cardDetailsEntity.getBillingAddress().getCountry(), is(testCardDetails.getBillingAddress().getCountry()));
    }

    @Test
    public void persist_shouldStoreCardDetails() {
        GatewayAccountEntity testAccount = new GatewayAccountEntity("sandbox", new HashMap<>(), GatewayAccountEntity.Type.TEST);
        gatewayAccountDao.persist(testAccount);

        Address billingAddress = aValidAddress().build();
        //TODO change to fixture
        ChargeEntity chargeEntity = new ChargeEntity(2323L, "returnUrl", "description",
                ServicePaymentReference.of("ref"), testAccount, "email@email.test", SupportedLanguage.ENGLISH,
                false, null);
        CardDetailsEntity cardDetailsEntity = new CardDetailsEntity(FirstDigitsCardNumber.of("123456"),LastDigitsCardNumber.of("1258"), "Mr. Pay Mc Payment", "03/09", "VISA", new AddressEntity(billingAddress));
        chargeEntity.setCardDetails(cardDetailsEntity);
        chargeDao.persist(chargeEntity);

        Map<String, Object> cardDetailsSaved = databaseTestHelper.getChargeCardDetails(chargeEntity.getId());
        assertThat(cardDetailsSaved, hasEntry("last_digits_card_number", "1258"));
        assertThat(cardDetailsSaved, hasEntry("first_digits_card_number", "123456"));
        assertThat(cardDetailsSaved, hasEntry("cardholder_name", cardDetailsEntity.getCardHolderName()));
        assertThat(cardDetailsSaved, hasEntry("expiry_date", cardDetailsEntity.getExpiryDate()));
        assertThat(cardDetailsSaved, hasEntry("address_line1", cardDetailsEntity.getBillingAddress().getLine1()));
        assertThat(cardDetailsSaved, hasEntry("address_line2", cardDetailsEntity.getBillingAddress().getLine2()));
        assertThat(cardDetailsSaved, hasEntry("address_postcode", cardDetailsEntity.getBillingAddress().getPostcode()));
        assertThat(cardDetailsSaved, hasEntry("address_city", cardDetailsEntity.getBillingAddress().getCity()));
        assertThat(cardDetailsSaved, hasEntry("address_county", cardDetailsEntity.getBillingAddress().getCounty()));
        assertThat(cardDetailsSaved, hasEntry("address_country", cardDetailsEntity.getBillingAddress().getCountry()));
    }
}
