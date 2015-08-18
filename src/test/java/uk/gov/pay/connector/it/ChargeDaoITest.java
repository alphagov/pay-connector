package uk.gov.pay.connector.it;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.util.DropwizardAppWithPostgresRule;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class ChargeDaoITest {

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    private ChargeDao chargeDao;

    @Before
    public void setUp() throws Exception {
        chargeDao = new ChargeDao(app.getJdbi());
    }

    @Test
    public void insertANewChargeAndReturnTheId() throws Exception {
        long amount = 100;
        long chargeId = chargeDao.insertAmountAndReturnNewId(amount);
        assertThat(chargeId, is(1L));
    }

    @Test
    public void insertAmountAndThenGetAmountById() throws Exception {
        long expectedAmount = 101;
        long chargeId = chargeDao.insertAmountAndReturnNewId(expectedAmount);

        long actualAmount = chargeDao.getAmountById(chargeId);

        assertThat(actualAmount, is(expectedAmount));
    }
}
