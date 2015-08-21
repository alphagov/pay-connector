package uk.gov.pay.connector.it.dao;

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
    private long gateway_account = 564532435;

    @Before
    public void setUp() throws Exception {
        chargeDao = new ChargeDao(app.getJdbi());
        app.getDatabaseTestHelper().addGatewayAccount(gateway_account, "test_account");
    }

    @Test
    public void insertANewChargeAndReturnTheId() throws Exception {
        long amount = 100;
//        Charge charge = chargeDao.saveNewCharge(amount, gateway_account);
//        assertThat(charge.getStatus(), is("CREATED"));
//        assertThat(charge.getAmount(), is(100));
    }

    @Test
    public void insertAmountAndThenGetAmountById() throws Exception {
        long expectedAmount = 101;
//        UUID chargeId = chargeDao.saveNewCharge(expectedAmount, gateway_account).getId();

//        Charge charge = chargeDao.findById(chargeId);
//
//        assertThat(charge.getAmount(), is(expectedAmount));
//        assertThat(charge.getStatus(), is("CREATED"));
//        assertThat(charge.getGatewayAccount(), is(gateway_account));
    }
}
