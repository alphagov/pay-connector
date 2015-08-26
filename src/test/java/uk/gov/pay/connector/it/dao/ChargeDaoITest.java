package uk.gov.pay.connector.it.dao;

import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.PayDBIException;
import uk.gov.pay.connector.model.ChargeStatus;
import uk.gov.pay.connector.util.DropwizardAppWithPostgresRule;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static uk.gov.pay.connector.model.ChargeStatus.AUTHORIZATION_SUBMITTED;
import static uk.gov.pay.connector.model.ChargeStatus.AUTHORIZATION_SUCCESS;

public class ChargeDaoITest {

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    private ChargeDao chargeDao;
    private String gateway_account = "564532435";

    @Before
    public void setUp() throws Exception {
        chargeDao = new ChargeDao(app.getJdbi());
        app.getDatabaseTestHelper().addGatewayAccount(gateway_account, "test_account");
    }

    @Test
    public void insertANewChargeAndReturnTheId() throws Exception {
        long amount = 100;
        Map<String, Object> newCharge = newCharge(amount);

        long chargeId = chargeDao.saveNewCharge(newCharge);
        assertThat(chargeId, is(1L));
    }

    @Test
    public void insertAmountAndThenGetAmountById() throws Exception {
        long expectedAmount = 101;
        long chargeId = chargeDao.saveNewCharge(newCharge(expectedAmount));

        Map<String, Object> charge = chargeDao.findById(chargeId);

        assertThat(charge.get("amount"), is(expectedAmount));
        assertThat(charge.get("status"), is("CREATED"));
        assertThat(charge.get("gateway_account"), is(nullValue()));
    }

    @Test
    public void insertChargeAndThenUpdateStatus() throws Exception {
        long amount = 101;
        long chargeId = chargeDao.saveNewCharge(newCharge(amount));

        chargeDao.updateStatus(chargeId, AUTHORIZATION_SUBMITTED);

        Map<String, Object> charge = chargeDao.findById(chargeId);

        assertThat(charge.get("amount"), is(amount));
        assertThat(charge.get("status"), is("AUTHORIZATION SUBMITTED"));
        assertThat(charge.get("gateway_account"), is(nullValue()));
    }

    @Test
    public void throwDBIExceptionIfStatusNotUpdateForMissingCharge() throws Exception {

        long unknownId = 128457938450746L;
        ChargeStatus status = AUTHORIZATION_SUCCESS;

        expectedEx.expect(PayDBIException.class);
        expectedEx.expectMessage("Could not update charge '" + unknownId + "' with status " + status.toString());

        chargeDao.updateStatus(unknownId, status);

    }

    private ImmutableMap<String, Object> newCharge(long amount) {
        return ImmutableMap.of(
                "amount", amount,
                "gateway_account", gateway_account);
    }

}
