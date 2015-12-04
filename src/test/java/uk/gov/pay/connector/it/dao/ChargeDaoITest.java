package uk.gov.pay.connector.it.dao;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.PayDBIException;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.collect.Lists.newArrayList;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.util.TransactionId.randomId;

public class ChargeDaoITest {
    private static final String GATEWAY_ACCOUNT_ID = "564532435";
    private static final String RETURN_URL = "http://service.com/success-page";
    private static final String REFERENCE = "Test reference";
    private static final String DESCRIPTION = "Test description";
    private static final long AMOUNT = 101;

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    private ChargeDao chargeDao;
    private String chargeId;

    @Before
    public void setUp() throws Exception {
        chargeDao = new ChargeDao(app.getJdbi());
        app.getDatabaseTestHelper().addGatewayAccount(GATEWAY_ACCOUNT_ID, "test_account");

        chargeId = chargeDao.saveNewCharge(newCharge(AMOUNT));
    }

    @Test
    public void insertANewChargeAndReturnTheId() throws Exception {
        assertThat(chargeId, is("1"));
    }

    @Test
    public void insertAmountAndThenGetAmountById() throws Exception {
        Map<String, Object> charge = chargeDao.findById(chargeId).get();

        assertThat(charge.get("charge_id"), is(chargeId));
        assertThat(charge.get("amount"), is(AMOUNT));
        assertThat(charge.get("reference"), is(REFERENCE));
        assertThat(charge.get("description"), is(DESCRIPTION));
        assertThat(charge.get("status"), is(CREATED.getValue()));
        assertThat(charge.get("gateway_account_id"), is(GATEWAY_ACCOUNT_ID));
        assertThat(charge.get("return_url"), is(RETURN_URL));
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

    @Test
    public void invalidSizeOfFields() throws Exception {
        expectedEx.expect(RuntimeException.class);
        Map<String, Object> chargeData = new HashMap<>(newCharge(AMOUNT));
        chargeData.put("reference", randomAlphanumeric(512));
        chargeId = chargeDao.saveNewCharge(chargeData);
    }

    private ImmutableMap<String, Object> newCharge(long amount) {
        return ImmutableMap.of(
                "amount", amount,
                "reference", REFERENCE,
                "description", DESCRIPTION,
                "gateway_account_id", GATEWAY_ACCOUNT_ID,
                "return_url", RETURN_URL);
    }
}
