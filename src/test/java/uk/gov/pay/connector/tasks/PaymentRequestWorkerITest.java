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
import uk.gov.pay.connector.model.domain.RefundStatus;
import uk.gov.pay.connector.util.RandomIdGenerator;

import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static uk.gov.pay.connector.model.domain.GatewayAccountEntity.Type.TEST;

public class PaymentRequestWorkerITest extends TaskITestBase {
    private static AtomicLong nextId = new AtomicLong(10);

    private DatabaseFixtures.TestAccount defaultTestAccount;

    private PaymentRequestWorker worker;

    @Before
    public void setUp() {
        worker = env.getInstance(PaymentRequestWorker.class);
        insertTestAccount();
    }

    @Test
    public void shouldCreateChargeTransactionEvent() {
        DatabaseFixtures.TestCharge chargeEntity = createCharge();
        databaseTestHelper.addEvent(chargeEntity.getChargeId(), chargeEntity.getChargeStatus().getValue());

        Pair<Long, Long> ids = createPaymentRequest(chargeEntity);

        worker.execute();

        assertTransactionEventsFor(ids.getRight(), ChargeStatus.CREATED);
    }

    @Test
    public void shouldCreateChargeTransactionEventWithGatewayEventDate() {
        DatabaseFixtures.TestCharge chargeEntity = createCharge();
        final ZonedDateTime gatewayEventDate = ZonedDateTime.now(ZoneId.of("UTC"));
        databaseTestHelper.addEventWithGatewayEventDate(chargeEntity.getChargeId(), chargeEntity.getChargeStatus().getValue(), gatewayEventDate);

        Pair<Long, Long> ids = createPaymentRequest(chargeEntity);

        worker.execute();

        List<Map<String, Object>> refundTransactionEvents = databaseTestHelper.loadTransactionEvents(ids.getRight());
        assertThat(refundTransactionEvents.size(), is(1));
        assertThat(refundTransactionEvents.get(0).get("status"), is(RefundStatus.CREATED.name()));
        assertThat(refundTransactionEvents.get(0).get("gateway_event_date"), is(Timestamp.from(gatewayEventDate.toInstant())));
    }

    @Test
    public void shouldCreateChargeTransactionEvents() {
        DatabaseFixtures.TestCharge testCharge = createCharge();
        databaseTestHelper.addEvent(testCharge.getChargeId(), testCharge.getChargeStatus().getValue(), testCharge.getCreatedDate().plusSeconds(1));
        databaseTestHelper.addEvent(testCharge.getChargeId(), ChargeStatus.ENTERING_CARD_DETAILS.getValue(), testCharge.getCreatedDate().plusSeconds(10));

        long chargeTransactionId = createPaymentRequest(testCharge).getRight();

        worker.execute();

        assertTransactionEventsFor(chargeTransactionId, ChargeStatus.ENTERING_CARD_DETAILS, ChargeStatus.CREATED);
    }

    @Test
    public void shouldCreateChargeTransactionEventsForMultiplePaymentRequests() {
        DatabaseFixtures.TestCharge chargeEntity1 = createCharge();
        databaseTestHelper.addEvent(chargeEntity1.getChargeId(), chargeEntity1.getChargeStatus().getValue());
        long chargeTransactionId1 = createPaymentRequest(chargeEntity1).getRight();

        DatabaseFixtures.TestCharge chargeEntity2 = createCharge();
        databaseTestHelper.addEvent(chargeEntity2.getChargeId(), chargeEntity2.getChargeStatus().getValue());
        long chargeTransactionId2 = createPaymentRequest(chargeEntity2).getRight();

        worker.execute();

        assertTransactionEventsFor(chargeTransactionId1, ChargeStatus.CREATED);
        assertTransactionEventsFor(chargeTransactionId2, ChargeStatus.CREATED);
    }

    @Test
    public void shouldCreateRefundTransactionEvents() throws InterruptedException {
        DatabaseFixtures.TestCharge testCharge = createCharge();
        DatabaseFixtures.TestRefund testRefund = createRefund(testCharge);
        addRefundHistoryEvent(testRefund, RefundStatus.REFUND_SUBMITTED, testRefund.getCreatedDate().plusSeconds(1));
        addRefundHistoryEvent(testRefund, RefundStatus.REFUNDED, testRefund.getCreatedDate().plusSeconds(10));
        long paymentRequestId = createPaymentRequest(testCharge).getLeft();
        List<Long> refundTransactionIds = addRefundTransactions(paymentRequestId, testRefund);

        worker.execute();

        assertTransactionEventsFor(refundTransactionIds.get(0), RefundStatus.REFUNDED, RefundStatus.REFUND_SUBMITTED);
    }

    @Test
    public void shouldCreateRefundTransactionEventsWhenMultipleRefundsPerPaymentRequest() {
        DatabaseFixtures.TestCharge chargeEntity = createCharge();
        DatabaseFixtures.TestRefund testRefund1 = createRefund(chargeEntity);
        addRefundHistoryEvent(testRefund1, RefundStatus.REFUND_SUBMITTED, testRefund1.getCreatedDate().plusSeconds(1));
        addRefundHistoryEvent(testRefund1, RefundStatus.REFUNDED, testRefund1.getCreatedDate().plusSeconds(10));

        DatabaseFixtures.TestRefund testRefund2 = createRefund(chargeEntity);
        addRefundHistoryEvent(testRefund2, RefundStatus.REFUND_SUBMITTED, testRefund2.getCreatedDate().plusSeconds(1));
        addRefundHistoryEvent(testRefund2, RefundStatus.REFUND_ERROR, testRefund1.getCreatedDate().plusSeconds(10));

        long paymentRequestId = createPaymentRequest(chargeEntity).getLeft();
        List<Long> refundTransactionIds = addRefundTransactions(paymentRequestId, testRefund1, testRefund2);

        worker.execute();

        assertTransactionEventsFor(refundTransactionIds.get(0), RefundStatus.REFUNDED, RefundStatus.REFUND_SUBMITTED);
        assertTransactionEventsFor(refundTransactionIds.get(1), RefundStatus.REFUND_ERROR, RefundStatus.REFUND_SUBMITTED);
    }

    @Test
    public void shouldCreateNewRefundTransactionEventsForPaymentRequestWithRefundTransactionEvents() {
        DatabaseFixtures.TestCharge chargeEntity = createCharge();
        DatabaseFixtures.TestRefund testRefund = createRefund(chargeEntity);
        addRefundHistoryEvent(testRefund, RefundStatus.REFUND_SUBMITTED, testRefund.getCreatedDate().plusSeconds(1));
        addRefundHistoryEvent(testRefund, RefundStatus.REFUNDED, testRefund.getCreatedDate().plusSeconds(10));

        Pair<Long, Long> ids = createPaymentRequest(chargeEntity);
        long paymentRequestId = ids.getLeft();
        List<Long> refundTransactionIds = addRefundTransactions(paymentRequestId, testRefund);
        Long refundTransactionId = refundTransactionIds.get(0);
        databaseTestHelper.addChargeTransactionEvent(ids.getRight(), ChargeStatus.CAPTURED, ZonedDateTime.now());
        databaseTestHelper.addRefundTransactionEvent(refundTransactionId, RefundStatus.REFUND_SUBMITTED, ZonedDateTime.now().plusSeconds(10));

        worker.execute();

        assertTransactionEventsFor(refundTransactionId, RefundStatus.REFUND_SUBMITTED, RefundStatus.REFUNDED);
    }

    @Test
    public void shouldCreateNewChargeTransactionEventsForPaymentRequestWithChargeTransactionEvents() {
        DatabaseFixtures.TestCharge chargeEntity = createCharge();
        databaseTestHelper.addEvent(chargeEntity.getChargeId(), chargeEntity.getChargeStatus().getValue());
        databaseTestHelper.addEvent(chargeEntity.getChargeId(), ChargeStatus.ENTERING_CARD_DETAILS.getValue());

        Pair<Long, Long> ids = createPaymentRequest(chargeEntity);
        Long chargeTransactionId = ids.getRight();
        databaseTestHelper.addChargeTransactionEvent(chargeTransactionId, ChargeStatus.ENTERING_CARD_DETAILS, ZonedDateTime.now());

        worker.execute();

        assertTransactionEventsFor(chargeTransactionId, ChargeStatus.ENTERING_CARD_DETAILS, ChargeStatus.CREATED);
    }

    @Test
    public void shouldCreateARefundTransactionForAPaymentRequestWithoutOneButARefundExists() throws Exception {
        DatabaseFixtures.TestCharge testCharge = createCharge();
        DatabaseFixtures.TestRefund testRefund = createRefund(testCharge);
        Pair<Long, Long> ids = createPaymentRequest(testCharge);

        worker.execute();

        assertRefundTransactions(ids.getLeft(), testRefund);
    }

    @Test
    public void shouldNotCreateAnotherRefundTransactionForAPaymentRequestWhereTheRefundTransactionAlreadyExists() throws Exception {
        DatabaseFixtures.TestCharge testCharge = createCharge();
        DatabaseFixtures.TestRefund testRefund = createRefund(testCharge);
        Pair<Long, Long> ids = createPaymentRequest(testCharge);
        List<Long> refundTransactionIds = addRefundTransactions(ids.getLeft(), testRefund);

        worker.execute();

        List<Map<String, Object>> refundTransactions = databaseTestHelper.getRefundTransaction(ids.getLeft());
        assertThat(refundTransactions.size(), is(1));
        Map<String, Object> refundTransaction = refundTransactions.get(0);
        assertThat(refundTransaction.get("id"), is(refundTransactionIds.get(0)));
    }

    @Test
    public void shouldCreateMultipleRefundTransactionForAPaymentRequestWithoutAnyButRefundsExists() throws Exception {
        DatabaseFixtures.TestCharge testCharge = createCharge();
        DatabaseFixtures.TestRefund testRefund1 = createRefund(testCharge);
        DatabaseFixtures.TestRefund testRefund2 = createRefund(testCharge);
        Pair<Long, Long> ids = createPaymentRequest(testCharge);

        worker.execute();

        assertRefundTransactions(ids.getLeft(), testRefund1, testRefund2);
    }

    @Test
    public void shouldOnlyCreateMissingRefundTransactionForAPaymentRequestWithSomeTransactionsButRefundsExists() throws Exception {
        DatabaseFixtures.TestCharge testCharge = createCharge();
        DatabaseFixtures.TestRefund testRefund1 = createRefund(testCharge);
        DatabaseFixtures.TestRefund testRefund2 = createRefund(testCharge);
        Pair<Long, Long> ids = createPaymentRequest(testCharge);
        addRefundTransactions(ids.getLeft(), testRefund1);

        worker.execute();

        assertRefundTransactions(ids.getLeft(), testRefund1, testRefund2);
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

    private DatabaseFixtures.TestRefund createRefund(DatabaseFixtures.TestCharge testCharge) {
        DatabaseFixtures.TestRefund testRefund = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestRefund()
                .withReference(randomAlphanumeric(10))
                .withTestCharge(testCharge)
                .insert();


        return testRefund;
    }

    private void addRefundHistoryEvent(DatabaseFixtures.TestRefund refundEntity, RefundStatus refundSubmitted, ZonedDateTime updatedDate) {
        databaseTestHelper.addRefundHistory(
                nextId.getAndIncrement(),
                refundEntity.getExternalRefundId(),
                refundEntity.getReference(),
                refundEntity.getAmount(),
                refundSubmitted.getValue(),
                refundEntity.getTestCharge().getChargeId(),
                updatedDate,
                updatedDate,
                refundEntity.getSubmittedByUserExternalId()
        );
    }

    private Pair<Long, Long> createPaymentRequest(DatabaseFixtures.TestCharge chargeEntity) {
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

        return new ImmutablePair<>(paymentRequestId, transactionId);
    }

    private List<Long> addRefundTransactions(long paymentRequestId, DatabaseFixtures.TestRefund... refundEntities) {
        List<Long> refundTransactionsIds = new ArrayList<>();
        for (DatabaseFixtures.TestRefund refundEntity : refundEntities) {
            long refundTransactionId = nextId.getAndIncrement();
            refundTransactionsIds.add(refundTransactionId);
            databaseTestHelper.addRefundTransaction(
                    refundTransactionId,
                    paymentRequestId,
                    refundEntity.getAmount(),
                    refundEntity.getExternalRefundId(),
                    refundEntity.getSubmittedByUserExternalId(),
                    refundEntity.getStatus(),
                    refundEntity.getReference()
            );
        }

        return refundTransactionsIds;
    }

    private void insertTestAccount() {
        this.defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .insert();
    }

    private void assertTransactionEventsFor(Long transactionId, RefundStatus... statuses) {
        assertTransactionEventsFor(transactionId, stream(statuses).map(Enum::name).toArray(String[]::new));
    }

    private void assertTransactionEventsFor(Long transactionId, ChargeStatus... statuses) {
        assertTransactionEventsFor(transactionId, stream(statuses).map(Enum::name).toArray(String[]::new));
    }

    private void assertTransactionEventsFor(Long transactionId, String... statuses) {
        List<Map<String, Object>> refundTransactionEvents = databaseTestHelper.loadTransactionEvents(transactionId);
        assertThat(refundTransactionEvents.size(), is(statuses.length));

        for (int index = 0; index < statuses.length; index++) {
            assertThat(refundTransactionEvents.get(index).get("status"), is(statuses[index]));
        }
    }

    private void assertRefundTransactions(Long paymentRequestId, DatabaseFixtures.TestRefund... testRefunds) {
        List<Map<String, Object>> refundTransactions = databaseTestHelper.getRefundTransaction(paymentRequestId);
        assertThat(refundTransactions.size(), is(testRefunds.length));
        List<String> refundReferences = refundTransactions.stream().map(refund -> (String)refund.get("refund_reference")).collect(toList());

        assertThat(refundReferences, containsInAnyOrder(stream(testRefunds).map(DatabaseFixtures.TestRefund::getReference).toArray()));
    }
}
