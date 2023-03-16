package uk.gov.pay.connector.it.dao;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.paymentinstrument.dao.PaymentInstrumentDao;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.apache.commons.lang.math.RandomUtils.nextLong;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

public class PaymentInstrumentDaoIT extends DaoITestBase {

    private PaymentInstrumentDao paymentInstrumentDao;
    private DatabaseFixtures.TestAccount defaultTestAccount;
    private DatabaseFixtures.TestCardDetails defaultTestCardDetails;
    
    private static final String PAYMENT_INSTRUMENT_EXTERNAL_ID_ONE = "12345678901234567890123456";
    private static final String PAYMENT_INSTRUMENT_EXTERNAL_ID_TWO= "99912345678901234567890123";
   
    @Before
    public void setUp() {
        paymentInstrumentDao = env.getInstance(PaymentInstrumentDao.class);

    }

    @Test
    public void findByExternalId_shouldFindAPaymentInstrumentEntity() {
        insertTestPaymentInstrument(PAYMENT_INSTRUMENT_EXTERNAL_ID_ONE);
        Optional<PaymentInstrumentEntity> paymentInstrument = paymentInstrumentDao.findByExternalId(PAYMENT_INSTRUMENT_EXTERNAL_ID_ONE);
        assertThat(paymentInstrument.isPresent(), is(true));
        assertThat(paymentInstrument.get().getExternalId(), is(PAYMENT_INSTRUMENT_EXTERNAL_ID_ONE));
    }

    @Test
    public void findByExternalId_shouldNotFindAPaymentInstrumentEntity() {
        Optional<PaymentInstrumentEntity> paymentInstrument = paymentInstrumentDao.findByExternalId(PAYMENT_INSTRUMENT_EXTERNAL_ID_ONE);
        assertThat(paymentInstrument.isPresent(), is(false));
    }

    @Test
    public void shouldFindByAgreementIdAndStatus() {
        String agreementExternalId = "an-agreement-id";
        String otherAgreementExternalId = "other-agreement-id";
        insertAgreement(agreementExternalId);
        insertAgreement(otherAgreementExternalId);

        insertTestPaymentInstrument("payment-instrument-1", agreementExternalId, 
                PaymentInstrumentStatus.ACTIVE);
        insertTestPaymentInstrument("payment-instrument-2", agreementExternalId, 
                PaymentInstrumentStatus.ACTIVE);
        insertTestPaymentInstrument("payment-instrument-3", agreementExternalId, 
                PaymentInstrumentStatus.CREATED);
        insertTestPaymentInstrument("payment-instrument-4", otherAgreementExternalId, 
                PaymentInstrumentStatus.ACTIVE);
        List<PaymentInstrumentEntity> paymentInstruments = paymentInstrumentDao.findPaymentInstrumentsByAgreementAndStatus(
                agreementExternalId, PaymentInstrumentStatus.ACTIVE);
        
        assertThat(paymentInstruments, hasSize(2));
        List<String> returnedPaymentInstrumentExternalIds = paymentInstruments.stream()
                .map(PaymentInstrumentEntity::getExternalId).collect(Collectors.toList());
        assertThat(returnedPaymentInstrumentExternalIds, containsInAnyOrder("payment-instrument-1", "payment-instrument-2"));
    }

    @After
    public void clear() {
        databaseTestHelper.truncateAllData();
    }
    
    private void insertTestPaymentInstrument(String paymentInstrumentExternalId) {
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestPaymentInstrument()
                .withPaymentInstrumentId(nextLong())
                .withExternalId(paymentInstrumentExternalId)
                .insert();
    }
    
    private void insertTestPaymentInstrument(String externalId, String agreementExternalId, PaymentInstrumentStatus status) {
        DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper)
                .aTestPaymentInstrument()
                .withExternalId(externalId)
                .withAgreementExternalId(agreementExternalId)
                .withStatus(status)
                .insert();
    }

    private static void insertAgreement(String agreementExternalId) {
        DatabaseFixtures.TestAccount testAccount = DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .insert();
        DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper)
                .aTestAgreement()
                .withExternalId(agreementExternalId)
                .withGatewayAccountId(testAccount.getAccountId())
                .insert();
    }
}
