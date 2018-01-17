package uk.gov.pay.connector.tasks;

import org.apache.commons.lang.math.RandomUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.it.tasks.TaskITestBase;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.UTCDateTimeConverter;
import uk.gov.pay.connector.util.RandomIdGenerator;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.pay.connector.model.domain.GatewayAccountEntity.Type.TEST;

public class MigrateCaptureApprovedRetryEventsWorkerITest extends TaskITestBase {
    private static AtomicLong nextId = new AtomicLong(10);

    private DatabaseFixtures.TestAccount defaultTestAccount;

    private MigrateCaptureApprovedRetryEventsWorker worker;

    @Before
    public void setUp() {
        worker = env.getInstance(MigrateCaptureApprovedRetryEventsWorker.class);
        insertTestAccount();
    }

    @Test
    public void shouldAddCaptureApprovedRetryEventsThatDoNotExistToChargeTransaction() {
        DatabaseFixtures.TestCharge chargeEntity = createCharge();
        ZonedDateTime chargeEventUpdateDate1 = ZonedDateTime.now().minusSeconds(10);
        databaseTestHelper.addEvent(chargeEntity.getChargeId(), ChargeStatus.CAPTURE_APPROVED_RETRY.getValue(), chargeEventUpdateDate1);
        ZonedDateTime chargeEventUpdateDate2 = ZonedDateTime.now();
        databaseTestHelper.addEvent(chargeEntity.getChargeId(), ChargeStatus.CAPTURE_APPROVED_RETRY.getValue(), chargeEventUpdateDate2);

        Pair<Long, Long> ids = createPaymentRequest(chargeEntity, chargeEventUpdateDate2);

        worker.execute(1L);

        List<Map<String, Object>> events = databaseTestHelper.loadTransactionEvents(ids.getRight());

        assertThat(events.size(), is(2));
        assertEvent(chargeEventUpdateDate2, events.get(0));
        assertEvent(chargeEventUpdateDate1, events.get(1));
    }

    private void assertEvent(ZonedDateTime expectedUpdateDate, Map<String, Object> event) {
        assertThat(event.get("status"), is(ChargeStatus.CAPTURE_APPROVED_RETRY.name()));
        assertThat(event.get("updated"), is(new UTCDateTimeConverter().convertToDatabaseColumn(expectedUpdateDate)));
    }

    private DatabaseFixtures.TestCharge createCharge() {
        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity(
                defaultTestAccount.getPaymentProvider(), new HashMap<>(), TEST);
        gatewayAccount.setId(defaultTestAccount.getAccountId());

        final Long chargeId = RandomUtils.nextLong();
        final String externalChargeId = RandomIdGenerator.newId();
        DatabaseFixtures.TestCharge testCharge = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withTransactionId("gatewayTransactionId");
        testCharge.insert();

        return testCharge;
    }

    private Pair<Long, Long> createPaymentRequest(DatabaseFixtures.TestCharge chargeEntity, ZonedDateTime captureApprovedRetryUpdateDate) {
        long paymentRequestId = nextId.getAndIncrement();
        databaseTestHelper.addPaymentRequest(
                paymentRequestId,
                chargeEntity.getAmount(),
                chargeEntity.getTestAccount().getAccountId(),
                chargeEntity.getReturnUrl(),
                chargeEntity.getDescription(),
                chargeEntity.getReference(),
                chargeEntity.getCreatedDate(),
                chargeEntity.getExternalChargeId());

        long transactionId = nextId.getAndIncrement();
        databaseTestHelper.addChargeTransaction(
                transactionId,
                chargeEntity.getTransactionId(),
                chargeEntity.getAmount(),
                chargeEntity.getChargeStatus(),
                paymentRequestId
        );

        databaseTestHelper.addChargeTransactionEvent(transactionId, ChargeStatus.CAPTURE_APPROVED_RETRY, captureApprovedRetryUpdateDate);

        return new ImmutablePair<>(paymentRequestId, transactionId);
    }

    private void insertTestAccount() {
        this.defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .insert();
    }
}
