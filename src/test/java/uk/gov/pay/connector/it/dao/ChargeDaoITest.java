package uk.gov.pay.connector.it.dao;

import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.PayDBIException;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;

import java.util.List;
import java.util.Map;

import static com.google.common.collect.Lists.newArrayList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.util.TransactionId.randomId;

public class ChargeDaoITest {

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    private ChargeDao chargeDao;
    private String gatewayAccountId = "564532435";
    private String returnUrl = "http://service.com/success-page";
    private String reference = "Test reference";
    private String description = "Test description";
    private long amount = 101;
    private String chargeId;

    @Before
    public void setUp() throws Exception {
        chargeDao = new ChargeDao(app.getJdbi());
        app.getDatabaseTestHelper().addGatewayAccount(gatewayAccountId, "test_account");

        chargeId = chargeDao.saveNewCharge(newCharge(amount));
    }

    @Test
    public void insertANewChargeAndReturnTheId() throws Exception {
        assertThat(chargeId, is("1"));
    }

    @Test
    public void insertAmountAndThenGetAmountById() throws Exception {
        Map<String, Object> charge = chargeDao.findById(chargeId).get();

        assertThat(charge.get("charge_id"), is(chargeId));
        assertThat(charge.get("amount"), is(amount));
        assertThat(charge.get("reference"), is(reference));
        assertThat(charge.get("description"), is(description));
        assertThat(charge.get("status"), is(CREATED.getValue()));
        assertThat(charge.get("gateway_account_id"), is(gatewayAccountId));
        assertThat(charge.get("return_url"), is(returnUrl));
    }

    @Test
    public void insertChargeAndThenUpdateStatus() throws Exception {
        chargeDao.updateStatus(chargeId, AUTHORISATION_SUBMITTED);

        Map<String, Object> charge = chargeDao.findById(chargeId).get();

        assertThat(charge.get("status"), is(AUTHORISATION_SUBMITTED.getValue()));
    }

    @Test
    public void insertChargeAndThenUpdateGatewayTransactionId() throws Exception {
        String transactionId = randomId();
        chargeDao.updateGatewayTransactionId(chargeId, transactionId);

        Map<String, Object> charge = chargeDao.findById(chargeId).get();
        assertThat(charge.get("gateway_transaction_id"), is(transactionId));
    }

    @Test
    public void insertChargeAndThenUpdateStatusPerGatewayTransactionId() throws Exception {
        String gatewayTransactionId = randomId();

        chargeDao.updateGatewayTransactionId(chargeId, gatewayTransactionId);
        chargeDao.updateStatusWithGatewayInfo(gatewayTransactionId, AUTHORISATION_SUBMITTED);

        Map<String, Object> charge = chargeDao.findById(chargeId).get();
        assertThat(charge.get("status"), is(AUTHORISATION_SUBMITTED.getValue()));
    }

    @Test
    public void updateStatusToEnteringCardDetailsFromCreated_shouldReturnOne() throws Exception {
        List<ChargeStatus> oldStatuses = newArrayList(CREATED, ENTERING_CARD_DETAILS);
        int rowsUpdated = chargeDao.updateNewStatusWhereOldStatusIn(chargeId, ENTERING_CARD_DETAILS, oldStatuses);

        assertEquals(1, rowsUpdated);
        Map<String, Object> charge = chargeDao.findById(chargeId).get();
        assertThat(charge.get("charge_id"), is(chargeId));
        assertThat(charge.get("status"), is(ENTERING_CARD_DETAILS.getValue()));
    }

    @Test
    public void updateStatusToEnteringCardDetailsFromCaptured_shouldReturnZero() throws Exception {
        chargeDao.updateStatus(chargeId, CAPTURE_SUBMITTED);
        List<ChargeStatus> oldStatuses = newArrayList(CREATED, ENTERING_CARD_DETAILS);
        int rowsUpdated = chargeDao.updateNewStatusWhereOldStatusIn(chargeId, ENTERING_CARD_DETAILS, oldStatuses);

        assertEquals(0, rowsUpdated);
        Map<String, Object> charge = chargeDao.findById(chargeId).get();
        assertThat(charge.get("charge_id"), is(chargeId));
        assertThat(charge.get("status"), is(CAPTURE_SUBMITTED.getValue()));
    }

    @Test
    public void throwDBIExceptionIfStatusNotUpdateForMissingCharge() throws Exception {
        String unknownId = "128457938450746";
        ChargeStatus status = AUTHORISATION_SUCCESS;

        expectedEx.expect(PayDBIException.class);
        expectedEx.expectMessage("Could not update charge '" + unknownId + "' with status " + status.toString());

        chargeDao.updateStatus(unknownId, status);
    }

    private ImmutableMap<String, Object> newCharge(long amount) {
        return ImmutableMap.of(
                "amount", amount,
                "reference", reference,
                "description", description,
                "gateway_account_id", gatewayAccountId,
                "return_url", returnUrl);
    }
}
