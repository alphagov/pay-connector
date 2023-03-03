package uk.gov.pay.connector.it.dao;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.paymentinstrument.dao.PaymentInstrumentDao;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;

import java.util.Optional;

import static org.apache.commons.lang.math.RandomUtils.nextLong;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;

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
}
