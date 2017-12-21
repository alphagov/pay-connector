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
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class AddTransactionIdToCardsWorkerITest extends TaskITestBase {
    private static AtomicLong nextId = new AtomicLong(10);

    private DatabaseFixtures.TestAccount defaultTestAccount;

    private AddTransactionIdToCardsWorker worker;

    @Before
    public void setUp() {
        worker = env.getInstance(AddTransactionIdToCardsWorker.class);
        insertTestAccount();
    }

    @Test
    public void shouldAddTransactionIdToCard() {
        DatabaseFixtures.TestCharge testCharge = addCharge();
        Pair<Long, Long> ids = createPaymentRequest(testCharge);
        long cardId = nextId.getAndIncrement();
        databaseTestHelper.addCard(cardId, testCharge.getChargeId(), null);

        worker.execute(1L);

        Map<String, Object> card = databaseTestHelper.getCard(cardId);
        assertThat(card.get("transaction_id"), is(ids.getRight()));
    }

    @Test
    public void shouldNotModifyCardThatAlreadyHasATransactionId() {
        DatabaseFixtures.TestCharge testCharge = addCharge();
        Pair<Long, Long> ids = createPaymentRequest(testCharge);
        long cardId = nextId.getAndIncrement();
        databaseTestHelper.addCard(cardId, null, ids.getRight());

        Map<String, Object> originalCard = databaseTestHelper.getCard(cardId);

        worker.execute(1L);

        Map<String, Object> card = databaseTestHelper.getCard(cardId);
        assertThat(card.get("version"), is(originalCard.get("version")));
        assertThat(card.get("transaction_id"), is(ids.getRight()));
    }

    @Test
    public void shouldOnlyAddTransactionIdToCardPastStartId() {
        DatabaseFixtures.TestCharge testCharge1 = addCharge();
        long cardId1 = nextId.getAndIncrement();
        databaseTestHelper.addCard(cardId1, testCharge1.getChargeId(), null);

        DatabaseFixtures.TestCharge testCharge2 = addCharge();
        Pair<Long, Long> ids2 = createPaymentRequest(testCharge2);
        long cardId2 = nextId.getAndIncrement();
        databaseTestHelper.addCard(cardId2, testCharge2.getChargeId(), null);

        worker.execute(cardId2);

        Map<String, Object> card1 = databaseTestHelper.getCard(cardId1);
        assertThat(card1.get("transaction_id"), nullValue());

        Map<String, Object> card2 = databaseTestHelper.getCard(cardId2);
        assertThat(card2.get("transaction_id"), is(ids2.getRight()));
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
