package uk.gov.pay.connector.tasks;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.it.tasks.TaskITestBase;
import uk.gov.pay.connector.util.RandomIdGenerator;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class AddTransactionIdToCard3dsWorkerITest extends TaskITestBase {
    private static AtomicLong nextId = new AtomicLong(10);

    private DatabaseFixtures.TestAccount defaultTestAccount;

    private AddTransactionIdToCard3dsWorker worker;

    @Before
    public void setUp() {
        worker = env.getInstance(AddTransactionIdToCard3dsWorker.class);
        insertTestAccount();
    }

    @Test
    public void shouldAddTransactionIdToCard3ds() throws Exception {
        DatabaseFixtures.TestCharge testCharge = addCharge();
        Pair<Long, Long> ids = createPaymentRequest(testCharge);
        long cardId = nextId.getAndIncrement();
        databaseTestHelper.addCard3ds(cardId, testCharge.getChargeId(), null);

        worker.execute();

        Map<String, Object> card = databaseTestHelper.getCard3ds(cardId);
        assertThat(card.get("transaction_id"), is(ids.getRight()));
    }

    @Test
    public void shouldNotModifyCard3dsThatAlreadyHasATransactionId() throws Exception {
        DatabaseFixtures.TestCharge testCharge = addCharge();
        Pair<Long, Long> ids = createPaymentRequest(testCharge);
        long cardId = nextId.getAndIncrement();
        databaseTestHelper.addCard3ds(cardId, testCharge.getChargeId(), ids.getRight());

        Map<String, Object> originalCard = databaseTestHelper.getCard3ds(cardId);

        worker.execute();

        Map<String, Object> card = databaseTestHelper.getCard3ds(cardId);
        assertThat(card.get("version"), is(originalCard.get("version")));
        assertThat(card.get("transaction_id"), is(ids.getRight()));
    }

    private DatabaseFixtures.TestCharge addCharge() {
        long chargeId = nextId.getAndIncrement();
        String externalChargeId = RandomIdGenerator.newId();
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


    private void insertTestAccount() {
        this.defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .insert();
    }
}
