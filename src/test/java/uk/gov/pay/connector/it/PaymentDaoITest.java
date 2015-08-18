package uk.gov.pay.connector.it;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.dao.PaymentDao;
import uk.gov.pay.connector.util.DropwizardAppWithPostgresRule;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class PaymentDaoITest {

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    private PaymentDao paymentDao;

    @Before
    public void setUp() throws Exception {
        paymentDao = new PaymentDao(app.getJdbi());
    }

    @Test
    public void insertANewPaymentAndReturnTheId() throws Exception {
        long amount = 100;
        long payId = paymentDao.insertAmountAndReturnNewId(amount);
        assertThat(payId, is(1L));
    }

    @Test
    public void insertAmountAndThenGetAmountById() throws Exception {
        long expectedAmount = 101;
        long payId = paymentDao.insertAmountAndReturnNewId(expectedAmount);

        long actualAmount = paymentDao.getAmountById(payId);

        assertThat(actualAmount, is((long)expectedAmount));
    }
}
