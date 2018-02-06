package uk.gov.pay.connector.tasks;

import org.apache.commons.lang.math.RandomUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.it.tasks.TaskITestBase;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.util.RandomIdGenerator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.pay.connector.model.domain.GatewayAccountEntity.Type.TEST;

public class BackfillGatewayAccountIdOnTransactionsWorkerITest extends TaskITestBase {
    private static AtomicLong nextId = new AtomicLong(10);

    private DatabaseFixtures.TestAccount defaultTestAccount;

    private BackfillGatewayAccountIdOnTransactionsWorker worker;

    @Before
    public void setUp() {
        worker = env.getInstance(BackfillGatewayAccountIdOnTransactionsWorker.class);
        insertTestAccount();
    }

    @Test
    public void shouldAddGatewayAccountIdToChargeTransaction() {
        DatabaseFixtures.TestCharge testCharge = createCharge();
        long expectedGatewayAccountId = testCharge.getTestAccount().getAccountId();
        Pair<Long, Long> ids = createPaymentRequest(testCharge);
        worker.execute(1L);
        final Map<String, Object> chargeTransaction = databaseTestHelper.getChargeTransaction(ids.getLeft());
        assertThat(chargeTransaction.get("gateway_account_id"), is(expectedGatewayAccountId));

    }

    @Test
    public void shouldAddGatewayAccountIdToRefundTransaction() {
        DatabaseFixtures.TestCharge testCharge = createCharge();
        DatabaseFixtures.TestRefund testRefund = createRefund(testCharge);
        long expectedGatewayAccountId = testCharge.getTestAccount().getAccountId();

        Pair<Long, Long> ids = createPaymentRequest(testCharge);
        addRefundTransactions(ids.getLeft(), testRefund);
        worker.execute(1L);
        final List<Map<String, Object>> refundTransactions = databaseTestHelper.getRefundTransactions(ids.getLeft());
        refundTransactions.forEach(refundTransaction ->
                assertThat(refundTransaction.get("gateway_account_id"), is(expectedGatewayAccountId))
        );
    }

    @Test
    public void shouldAddGatewayAccountIdToMultipleRefundTransactions() {
        DatabaseFixtures.TestCharge testCharge = createCharge();
        DatabaseFixtures.TestRefund testRefund1 = createRefund(testCharge);
        DatabaseFixtures.TestRefund testRefund2 = createRefund(testCharge);
        DatabaseFixtures.TestRefund testRefund3 = createRefund(testCharge);
        long expectedGatewayAccountId = testCharge.getTestAccount().getAccountId();

        Pair<Long, Long> ids = createPaymentRequest(testCharge);
        addRefundTransactions(ids.getLeft(), testRefund1);
        addRefundTransactions(ids.getLeft(), testRefund2);
        addRefundTransactions(ids.getLeft(), testRefund3);
        worker.execute(1L);
        final List<Map<String, Object>> refundTransactions = databaseTestHelper.getRefundTransactions(ids.getLeft());
        final Map<String, Object> refundObject1 = refundTransactions.get(0);
        assertThat(refundObject1.get("gateway_account_id"), is(expectedGatewayAccountId));
        final Map<String, Object> refundObject2 = refundTransactions.get(1);
        assertThat(refundObject2.get("gateway_account_id"), is(expectedGatewayAccountId));
        final Map<String, Object> refundObject3 = refundTransactions.get(2);
        assertThat(refundObject3.get("gateway_account_id"), is(expectedGatewayAccountId));
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
                .withReference(randomAlphanumeric(10).trim())
                .withTestCharge(testCharge)
                .insert();


        return testRefund;
    }

    private void insertTestAccount() {
        this.defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .insert();
    }
}
