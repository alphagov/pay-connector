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
import static uk.gov.pay.connector.model.ChargeStatus.AUTHORIZATION_SUBMITTED;
import static uk.gov.pay.connector.model.ChargeStatus.AUTHORIZATION_SUCCESS;

public class ChargeDaoITest {

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    private ChargeDao chargeDao;
    private String gatewayAccountId = "564532435";
    private String returnUrl = "http://service.com/success-page";

    @Before
    public void setUp() throws Exception {
        chargeDao = new ChargeDao(app.getJdbi());
        app.getDatabaseTestHelper().addGatewayAccount(gatewayAccountId, "test_account");
    }

    @Test
    public void insertANewChargeAndReturnTheId() throws Exception {
        long amount = 100;
        Map<String, Object> newCharge = newCharge(amount);

        String chargeId = chargeDao.saveNewCharge(newCharge);
        assertThat(chargeId, is("1"));
    }

    @Test
    public void insertAmountAndThenGetAmountById() throws Exception {
        long expectedAmount = 101;
        String chargeId = chargeDao.saveNewCharge(newCharge(expectedAmount));

        Map<String, Object> charge = chargeDao.findById(chargeId).get();

        assertThat(charge.get("charge_id"), is(chargeId));
        assertThat(charge.get("amount"), is(expectedAmount));
        assertThat(charge.get("status"), is("CREATED"));
        assertThat(charge.get("gateway_account_id"), is(gatewayAccountId));
        assertThat(charge.get("return_url"), is(returnUrl));
    }

    @Test
    public void insertChargeAndThenUpdateStatus() throws Exception {
        long amount = 101;
        String chargeId = chargeDao.saveNewCharge(newCharge(amount));

        chargeDao.updateStatus(chargeId, AUTHORIZATION_SUBMITTED);

        Map<String, Object> charge = chargeDao.findById(chargeId).get();

        assertThat(charge.get("charge_id"), is(chargeId));
        assertThat(charge.get("amount"), is(amount));
        assertThat(charge.get("status"), is("AUTHORIZATION SUBMITTED"));
        assertThat(charge.get("gateway_account_id"), is(gatewayAccountId));
        assertThat(charge.get("return_url"), is(returnUrl));
    }

    @Test
    public void throwDBIExceptionIfStatusNotUpdateForMissingCharge() throws Exception {

        String unknownId = "128457938450746";
        ChargeStatus status = AUTHORIZATION_SUCCESS;

        expectedEx.expect(PayDBIException.class);
        expectedEx.expectMessage("Could not update charge '" + unknownId + "' with status " + status.toString());

        chargeDao.updateStatus(unknownId, status);

    }

    @Test
    public void throwsException_WhenMissingFields() throws Exception {
        expectedEx.expect(PayDBIException.class);
        expectedEx.expectMessage("Field(s) missing: amount, gateway_account_id, return_url");

        chargeDao.saveNewCharge(ImmutableMap.of());
    }

    private ImmutableMap<String, Object> newCharge(long amount) {
        return ImmutableMap.of(
                "amount", amount,
                "gateway_account_id", gatewayAccountId,
                "return_url", returnUrl);
    }

}
