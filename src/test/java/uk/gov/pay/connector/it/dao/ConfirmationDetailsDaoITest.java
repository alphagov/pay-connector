package uk.gov.pay.connector.it.dao;

import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.dao.ConfirmationDetailsDao;
import uk.gov.pay.connector.model.domain.Address;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.ConfirmationDetailsEntity;

import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static uk.gov.pay.connector.model.domain.Address.anAddress;


public class ConfirmationDetailsDaoITest extends DaoITestBase {
    private ConfirmationDetailsDao confirmationDetailsDao;

    private DatabaseFixtures.TestCharge chargeTestRecord;
    private DatabaseFixtures.TestConfirmationDetails confirmationDetailsTestRecord;
    @Before
    public void setUp() throws Exception {
        confirmationDetailsDao = env.getInstance(ConfirmationDetailsDao.class);

        DatabaseFixtures.TestAccount testAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount();

        DatabaseFixtures.TestCharge testCharge = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(testAccount)
                .withChargeStatus(ChargeStatus.CAPTURE_SUBMITTED);

        DatabaseFixtures.TestConfirmationDetails testConfirmationDetails = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestConfirmationDetails()
                .withChargeEntity(testCharge);

        testAccount.insert();
        this.chargeTestRecord = testCharge.insert();
        this.confirmationDetailsTestRecord = testConfirmationDetails.insert();
    }

    @Test
    public void findById_shouldFindConfirmationDetails() {
        Optional<ConfirmationDetailsEntity> confirmationDetailsEntityMaybe = confirmationDetailsDao.findById(confirmationDetailsTestRecord.getId());

        assertThat(confirmationDetailsEntityMaybe.isPresent(), is(true));

        ConfirmationDetailsEntity confirmationDetailsEntity = confirmationDetailsEntityMaybe.get();

        assertNotNull(confirmationDetailsEntity.getId());
        assertThat(confirmationDetailsEntity.getCardHolderName(), is(confirmationDetailsTestRecord.getCardHolderName()));
        assertThat(confirmationDetailsEntity.getLastDigitsCardNumber(), is(confirmationDetailsTestRecord.getLastDigitsCardNumber()));
        assertThat(confirmationDetailsEntity.getExpiryDate(), is(confirmationDetailsTestRecord.getExpiryDate()));
        assertNotNull(confirmationDetailsEntity.getBillingAddress());
        assertThat(confirmationDetailsEntity.getBillingAddress().getLine1(), is(confirmationDetailsTestRecord.getBillingAddress().getLine1()));
        assertThat(confirmationDetailsEntity.getBillingAddress().getLine2(), is(confirmationDetailsTestRecord.getBillingAddress().getLine2()));
        assertThat(confirmationDetailsEntity.getBillingAddress().getPostcode(), is(confirmationDetailsTestRecord.getBillingAddress().getPostcode()));
        assertThat(confirmationDetailsEntity.getBillingAddress().getCity(), is(confirmationDetailsTestRecord.getBillingAddress().getCity()));
        assertThat(confirmationDetailsEntity.getBillingAddress().getCounty(), is(confirmationDetailsTestRecord.getBillingAddress().getCounty()));
        assertThat(confirmationDetailsEntity.getBillingAddress().getCountry(), is(confirmationDetailsTestRecord.getBillingAddress().getCountry()));
        assertNotNull(confirmationDetailsEntity.getChargeEntity());
        assertThat(confirmationDetailsEntity.getChargeEntity().getId(), is(confirmationDetailsTestRecord.getChargeEntity().getChargeId()));
        assertNotNull(confirmationDetailsEntity.getVersion());
    }

    @Test
    public void findById_shouldNotFindConfirmationDetails() {
        long notExistingConfirmationDetailsId = 0L;
        assertThat(confirmationDetailsDao.findById(notExistingConfirmationDetailsId).isPresent(), is(false));
    }

    @Test
    public void findByChargeId_shouldFindConfirmationDetails() {
        Optional<ConfirmationDetailsEntity> confirmationDetailsEntityMaybe = confirmationDetailsDao.findByChargeId(chargeTestRecord.getChargeId());

        assertThat(confirmationDetailsEntityMaybe.isPresent(), is(true));

        ConfirmationDetailsEntity confirmationDetailsEntity = confirmationDetailsEntityMaybe.get();

        assertNotNull(confirmationDetailsEntity.getId());
        assertThat(confirmationDetailsEntity.getCardHolderName(), is(confirmationDetailsTestRecord.getCardHolderName()));
        assertThat(confirmationDetailsEntity.getLastDigitsCardNumber(), is(confirmationDetailsTestRecord.getLastDigitsCardNumber()));
        assertThat(confirmationDetailsEntity.getExpiryDate(), is(confirmationDetailsTestRecord.getExpiryDate()));
        assertNotNull(confirmationDetailsEntity.getBillingAddress());
        assertThat(confirmationDetailsEntity.getBillingAddress().getLine1(), is(confirmationDetailsTestRecord.getBillingAddress().getLine1()));
        assertThat(confirmationDetailsEntity.getBillingAddress().getLine2(), is(confirmationDetailsTestRecord.getBillingAddress().getLine2()));
        assertThat(confirmationDetailsEntity.getBillingAddress().getPostcode(), is(confirmationDetailsTestRecord.getBillingAddress().getPostcode()));
        assertThat(confirmationDetailsEntity.getBillingAddress().getCity(), is(confirmationDetailsTestRecord.getBillingAddress().getCity()));
        assertThat(confirmationDetailsEntity.getBillingAddress().getCounty(), is(confirmationDetailsTestRecord.getBillingAddress().getCounty()));
        assertThat(confirmationDetailsEntity.getBillingAddress().getCountry(), is(confirmationDetailsTestRecord.getBillingAddress().getCountry()));
        assertNotNull(confirmationDetailsEntity.getChargeEntity());
        assertThat(confirmationDetailsEntity.getChargeEntity().getId(), is(confirmationDetailsTestRecord.getChargeEntity().getChargeId()));
        assertNotNull(confirmationDetailsEntity.getVersion());
    }

    @Test
    public void findByChargeId_shouldNotFindConfirmationDetails() {
        long notExistingChargeId = 0L;
        assertThat(confirmationDetailsDao.findByChargeId(notExistingChargeId).isPresent(), is(false));
    }

    @Test
    public void persist_shouldCreateConfirmationDetails() {
        ChargeEntity chargeEntity = new ChargeEntity();
        chargeEntity.setId(chargeTestRecord.getChargeId());

        Address billingAddress = anAddress();
        billingAddress.setLine1("line1");
        billingAddress.setCity("city");
        billingAddress.setPostcode("postcode");
        billingAddress.setCountry("country");

        ConfirmationDetailsEntity confirmationDetailsEntity = new ConfirmationDetailsEntity(chargeEntity);
        confirmationDetailsEntity.setBillingAddress(billingAddress);
        confirmationDetailsEntity.setCardHolderName("Mr. Pay Mc Payment");
        confirmationDetailsEntity.setExpiryDate("03/09");
        confirmationDetailsEntity.setLastDigitsCardNumber("1258");
        confirmationDetailsDao.persist(confirmationDetailsEntity);

        assertNotNull(confirmationDetailsEntity.getId());

        Map<String, Object> confirmationDetailsByIdFound = databaseTestHelper.getConfirmationDetails(confirmationDetailsEntity.getId());
        assertThat(confirmationDetailsByIdFound, hasEntry("charge_id", confirmationDetailsEntity.getChargeEntity().getId()));
        assertThat(confirmationDetailsByIdFound, hasEntry("last_digits_card_number", confirmationDetailsEntity.getLastDigitsCardNumber()));
        assertThat(confirmationDetailsByIdFound, hasEntry("cardholder_name", confirmationDetailsEntity.getCardHolderName()));
        assertThat(confirmationDetailsByIdFound, hasEntry("expiry_date", confirmationDetailsEntity.getExpiryDate()));
        assertThat(confirmationDetailsByIdFound, hasEntry("address_line1", confirmationDetailsEntity.getBillingAddress().getLine1()));
        assertThat(confirmationDetailsByIdFound, hasEntry("address_line2", confirmationDetailsEntity.getBillingAddress().getLine2()));
        assertThat(confirmationDetailsByIdFound, hasEntry("address_postcode", confirmationDetailsEntity.getBillingAddress().getPostcode()));
        assertThat(confirmationDetailsByIdFound, hasEntry("address_city", confirmationDetailsEntity.getBillingAddress().getCity()));
        assertThat(confirmationDetailsByIdFound, hasEntry("address_county", confirmationDetailsEntity.getBillingAddress().getCounty()));
        assertThat(confirmationDetailsByIdFound, hasEntry("address_country", confirmationDetailsEntity.getBillingAddress().getCountry()));
    }
}