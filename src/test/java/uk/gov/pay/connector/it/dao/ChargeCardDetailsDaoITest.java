package uk.gov.pay.connector.it.dao;

import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.dao.ChargeCardDetailsDao;
import uk.gov.pay.connector.model.domain.*;

import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static uk.gov.pay.connector.model.domain.AddressFixture.aValidAddress;


public class ChargeCardDetailsDaoITest extends DaoITestBase {
    private ChargeCardDetailsDao chargeCardDetailsDao;
    private DatabaseFixtures.TestAccount testAccount;
    @Before
    public void setUp() throws Exception {
        chargeCardDetailsDao = env.getInstance(ChargeCardDetailsDao.class);

        testAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount();
        testAccount.insert();
    }

    private DatabaseFixtures.TestConfirmationDetails createConfirmationDetailsForChargeWithId(long chargeId) {
        DatabaseFixtures.TestCharge testCharge = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withChargeId(chargeId)
                .withTestAccount(testAccount)
                .withChargeStatus(ChargeStatus.CAPTURE_SUBMITTED);

        DatabaseFixtures.TestConfirmationDetails testConfirmationDetails = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestConfirmationDetails()
                .withChargeEntity(testCharge);

        testCharge.insert();
        testConfirmationDetails.insert();
        return testConfirmationDetails;
    }

    @Test
    public void findById_shouldFindConfirmationDetails() {
        DatabaseFixtures.TestConfirmationDetails confirmationDetailsTestRecord = createConfirmationDetailsForChargeWithId(123L);
        Optional<ChargeCardDetailsEntity> confirmationDetailsEntityMaybe = chargeCardDetailsDao.findById(confirmationDetailsTestRecord.getId());

        assertThat(confirmationDetailsEntityMaybe.isPresent(), is(true));

        ChargeCardDetailsEntity chargeCardDetailsEntity = confirmationDetailsEntityMaybe.get();

        assertNotNull(chargeCardDetailsEntity.getId());
        assertThat(chargeCardDetailsEntity.getCardHolderName(), is(confirmationDetailsTestRecord.getCardHolderName()));
        assertThat(chargeCardDetailsEntity.getLastDigitsCardNumber(), is(confirmationDetailsTestRecord.getLastDigitsCardNumber()));
        assertThat(chargeCardDetailsEntity.getExpiryDate(), is(confirmationDetailsTestRecord.getExpiryDate()));
        assertNotNull(chargeCardDetailsEntity.getBillingAddress());
        assertThat(chargeCardDetailsEntity.getBillingAddress().getLine1(), is(confirmationDetailsTestRecord.getBillingAddress().getLine1()));
        assertThat(chargeCardDetailsEntity.getBillingAddress().getLine2(), is(confirmationDetailsTestRecord.getBillingAddress().getLine2()));
        assertThat(chargeCardDetailsEntity.getBillingAddress().getPostcode(), is(confirmationDetailsTestRecord.getBillingAddress().getPostcode()));
        assertThat(chargeCardDetailsEntity.getBillingAddress().getCity(), is(confirmationDetailsTestRecord.getBillingAddress().getCity()));
        assertThat(chargeCardDetailsEntity.getBillingAddress().getCounty(), is(confirmationDetailsTestRecord.getBillingAddress().getCounty()));
        assertThat(chargeCardDetailsEntity.getBillingAddress().getCountry(), is(confirmationDetailsTestRecord.getBillingAddress().getCountry()));
        assertNotNull(chargeCardDetailsEntity.getChargeEntity());
        assertThat(chargeCardDetailsEntity.getChargeEntity().getId(), is(confirmationDetailsTestRecord.getChargeEntity().getChargeId()));
        assertNotNull(chargeCardDetailsEntity.getVersion());
    }

    @Test
    public void findById_shouldNotFindConfirmationDetails() {
        long notExistingConfirmationDetailsId = 0L;
        assertThat(chargeCardDetailsDao.findById(notExistingConfirmationDetailsId).isPresent(), is(false));
    }

    @Test
    public void findByChargeId_shouldFindConfirmationDetails() {
        long chargeId = 123L;
        DatabaseFixtures.TestConfirmationDetails confirmationDetailsTestRecord = createConfirmationDetailsForChargeWithId(chargeId);
        Optional<ChargeCardDetailsEntity> confirmationDetailsEntityMaybe = chargeCardDetailsDao.findByChargeId(chargeId);

        assertThat(confirmationDetailsEntityMaybe.isPresent(), is(true));

        ChargeCardDetailsEntity chargeCardDetailsEntity = confirmationDetailsEntityMaybe.get();

        assertNotNull(chargeCardDetailsEntity.getId());
        assertThat(chargeCardDetailsEntity.getCardHolderName(), is(confirmationDetailsTestRecord.getCardHolderName()));
        assertThat(chargeCardDetailsEntity.getLastDigitsCardNumber(), is(confirmationDetailsTestRecord.getLastDigitsCardNumber()));
        assertThat(chargeCardDetailsEntity.getExpiryDate(), is(confirmationDetailsTestRecord.getExpiryDate()));
        assertNotNull(chargeCardDetailsEntity.getBillingAddress());
        assertThat(chargeCardDetailsEntity.getBillingAddress().getLine1(), is(confirmationDetailsTestRecord.getBillingAddress().getLine1()));
        assertThat(chargeCardDetailsEntity.getBillingAddress().getLine2(), is(confirmationDetailsTestRecord.getBillingAddress().getLine2()));
        assertThat(chargeCardDetailsEntity.getBillingAddress().getPostcode(), is(confirmationDetailsTestRecord.getBillingAddress().getPostcode()));
        assertThat(chargeCardDetailsEntity.getBillingAddress().getCity(), is(confirmationDetailsTestRecord.getBillingAddress().getCity()));
        assertThat(chargeCardDetailsEntity.getBillingAddress().getCounty(), is(confirmationDetailsTestRecord.getBillingAddress().getCounty()));
        assertThat(chargeCardDetailsEntity.getBillingAddress().getCountry(), is(confirmationDetailsTestRecord.getBillingAddress().getCountry()));
        assertNotNull(chargeCardDetailsEntity.getChargeEntity());
        assertThat(chargeCardDetailsEntity.getChargeEntity().getId(), is(confirmationDetailsTestRecord.getChargeEntity().getChargeId()));
        assertNotNull(chargeCardDetailsEntity.getVersion());
    }

    @Test
    public void findByChargeId_shouldNotFindConfirmationDetails() {
        long notExistingChargeId = 0L;
        assertThat(chargeCardDetailsDao.findByChargeId(notExistingChargeId).isPresent(), is(false));
    }

    @Test
    public void persist_shouldCreateConfirmationDetails() {
        DatabaseFixtures.TestCharge testCharge = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withChargeId(10L)
                .withTestAccount(testAccount)
                .withChargeStatus(ChargeStatus.CAPTURE_SUBMITTED);
        testCharge.insert();

        Address billingAddress = aValidAddress().build();

        ChargeEntity chargeEntity = new ChargeEntity();
        chargeEntity.setId(testCharge.getChargeId());
        ChargeCardDetailsEntity chargeCardDetailsEntity = new ChargeCardDetailsEntity(chargeEntity);
        chargeCardDetailsEntity.setBillingAddress(new AddressEntity(billingAddress));
        chargeCardDetailsEntity.setCardHolderName("Mr. Pay Mc Payment");
        chargeCardDetailsEntity.setExpiryDate("03/09");
        chargeCardDetailsEntity.setLastDigitsCardNumber("1258");
        chargeCardDetailsDao.persist(chargeCardDetailsEntity);

        assertNotNull(chargeCardDetailsEntity.getId());

        Map<String, Object> confirmationDetailsByIdFound = databaseTestHelper.getChargeCardDetails(chargeCardDetailsEntity.getId());
        assertThat(confirmationDetailsByIdFound, hasEntry("charge_id", chargeCardDetailsEntity.getChargeEntity().getId()));
        assertThat(confirmationDetailsByIdFound, hasEntry("last_digits_card_number", chargeCardDetailsEntity.getLastDigitsCardNumber()));
        assertThat(confirmationDetailsByIdFound, hasEntry("cardholder_name", chargeCardDetailsEntity.getCardHolderName()));
        assertThat(confirmationDetailsByIdFound, hasEntry("expiry_date", chargeCardDetailsEntity.getExpiryDate()));
        assertThat(confirmationDetailsByIdFound, hasEntry("address_line1", chargeCardDetailsEntity.getBillingAddress().getLine1()));
        assertThat(confirmationDetailsByIdFound, hasEntry("address_line2", chargeCardDetailsEntity.getBillingAddress().getLine2()));
        assertThat(confirmationDetailsByIdFound, hasEntry("address_postcode", chargeCardDetailsEntity.getBillingAddress().getPostcode()));
        assertThat(confirmationDetailsByIdFound, hasEntry("address_city", chargeCardDetailsEntity.getBillingAddress().getCity()));
        assertThat(confirmationDetailsByIdFound, hasEntry("address_county", chargeCardDetailsEntity.getBillingAddress().getCounty()));
        assertThat(confirmationDetailsByIdFound, hasEntry("address_country", chargeCardDetailsEntity.getBillingAddress().getCountry()));
    }

    @Test(expected = Exception.class)
    public void shouldNotInsertTwiceConfirmationDetailsForSameChargeId() {
        long chargeId = 123L;
        createConfirmationDetailsForChargeWithId(chargeId);
        createConfirmationDetailsForChargeWithId(chargeId);
    }
}
