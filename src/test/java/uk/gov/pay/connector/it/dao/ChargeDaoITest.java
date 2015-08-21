package uk.gov.pay.connector.it.dao;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.util.DropwizardAppWithPostgresRule;

import java.util.UUID;

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
        chargeDao.insertAmountAndReturnNewId(amount);
    }

    @Test
    public void insertAmountAndThenGetAmountById() throws Exception {
        long expectedAmount = 101;
        UUID chargeId = chargeDao.insertAmountAndReturnNewId(expectedAmount);

        long actualAmount = chargeDao.getAmountById(chargeId);

        assertThat(actualAmount, is(expectedAmount));
    }
}
