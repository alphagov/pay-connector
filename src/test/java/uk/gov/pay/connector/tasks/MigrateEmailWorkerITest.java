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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.pay.connector.model.domain.GatewayAccountEntity.Type.TEST;

public class MigrateEmailWorkerITest extends TaskITestBase {
    private static AtomicLong nextId = new AtomicLong(10);

    private DatabaseFixtures.TestAccount defaultTestAccount;

    private MigrateEmailWorker worker;

    @Before
    public void setUp() {
        worker = env.getInstance(MigrateEmailWorker.class);
        insertTestAccount();
    }

    @Test
    public void shouldAddEmailToMultipleChargeTransaction() {
        String emailAddress1 = "some@email.com";
        DatabaseFixtures.TestCharge testCharge1 = createChargeWithEmail(emailAddress1);
        Pair<Long, Long> ids1 = createPaymentRequest(testCharge1);

        String emailAddress2 = "some@email2.com";
        DatabaseFixtures.TestCharge testCharge2 = createChargeWithEmail(emailAddress2);
        Pair<Long, Long> ids2 = createPaymentRequest(testCharge2);

        worker.execute(1L);

        final Map<String, Object> chargeTransaction1 = databaseTestHelper.getChargeTransaction(ids1.getLeft());
        assertThat(chargeTransaction1.get("email"), is(emailAddress1));

        final Map<String, Object> chargeTransaction2 = databaseTestHelper.getChargeTransaction(ids2.getLeft());
        assertThat(chargeTransaction2.get("email"), is(emailAddress2));
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

    private DatabaseFixtures.TestCharge createChargeWithEmail(String emailAddress) {
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
                .withTransactionId("gatewayTransactionId")
                .withEmail(emailAddress);
        testCharge.insert();

        return testCharge;
    }

    private void insertTestAccount() {
        this.defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .insert();
    }
}
