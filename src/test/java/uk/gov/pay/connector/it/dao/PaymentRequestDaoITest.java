package uk.gov.pay.connector.it.dao;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import uk.gov.pay.connector.dao.PaymentRequestDao;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.PaymentRequestEntity;
import uk.gov.pay.connector.model.domain.transaction.ChargeTransactionEntity;
import uk.gov.pay.connector.service.ChargeStatusUpdater;

import java.util.HashMap;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static uk.gov.pay.connector.model.domain.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.model.domain.PaymentRequestEntityFixture.aValidPaymentRequestEntity;
import static uk.gov.pay.connector.model.domain.transaction.ChargeTransactionEntityBuilder.aChargeTransactionEntity;

public class PaymentRequestDaoITest extends DaoITestBase {
    private DatabaseFixtures.TestAccount defaultTestAccount;
    private PaymentRequestDao paymentRequestDao;
    private ChargeStatusUpdater chargeStatusUpdater;
    @Rule
    public ExpectedException expectedEx = ExpectedException.none();
    private GatewayAccountEntity gatewayAccount;


    @Before
    public void setUp() throws Exception {
        paymentRequestDao = env.getInstance(PaymentRequestDao.class);
        chargeStatusUpdater = env.getInstance(ChargeStatusUpdater.class);
        insertTestAccount();

        gatewayAccount = new GatewayAccountEntity(
                defaultTestAccount.getPaymentProvider(), new HashMap<>(), TEST);
        gatewayAccount.setId(defaultTestAccount.getAccountId());
    }

    @Test
    public void shouldCreateAPaymentRequest() throws Exception {
        PaymentRequestEntity paymentRequestEntity = aValidPaymentRequestEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .build();

        paymentRequestDao.persist(paymentRequestEntity);

        assertThat(paymentRequestEntity.getId(), is(notNullValue()));
        // Ensure always max precision is being millis
        assertThat(paymentRequestEntity.getCreatedDate().getNano() % 1000000, is(0));
        paymentRequestEntity.getTransactions().forEach(
                transactionEntity -> assertThat(transactionEntity.getId(), is(notNullValue()))
        );
    }

    @Test
    public void shouldUpdateTransactionStatusAndAddEvent() throws Exception {
        PaymentRequestEntity paymentRequestEntity = aValidPaymentRequestEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .build();

        paymentRequestDao.persist(paymentRequestEntity);

        chargeStatusUpdater.updateChargeTransactionStatus(paymentRequestEntity.getExternalId(), ChargeStatus.ENTERING_CARD_DETAILS);
        final Optional<PaymentRequestEntity> byExternalId =
                paymentRequestDao.findByExternalId(paymentRequestEntity.getExternalId());

        assertThat(byExternalId.isPresent(), is(true));
        ChargeTransactionEntity chargeTransaction = byExternalId.get().getChargeTransaction();
        assertThat(chargeTransaction.getStatus(), is(ChargeStatus.ENTERING_CARD_DETAILS));
        assertThat(chargeTransaction.getTransactionEvents().size(), is(2));
        assertThat(chargeTransaction.getTransactionEvents().get(0).getStatus(), is(ChargeStatus.ENTERING_CARD_DETAILS));
        assertThat(chargeTransaction.getTransactionEvents().get(1).getStatus(), is(ChargeStatus.CREATED));
    }

    @Test
    public void shouldPersistMultipleTransactions() throws Exception {
        PaymentRequestEntity paymentRequestEntity = aValidPaymentRequestEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withTransactions(aChargeTransactionEntity().build(), aChargeTransactionEntity().build()).build();

        paymentRequestDao.persist(paymentRequestEntity);
        paymentRequestEntity.getTransactions().forEach(
                transactionEntity -> assertThat(transactionEntity.getId(), is(notNullValue()))
        );
    }

    @Test
    public void shouldPersistATransaction() throws Exception {
        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity(
                defaultTestAccount.getPaymentProvider(), new HashMap<>(), TEST);
        gatewayAccount.setId(defaultTestAccount.getAccountId());

        PaymentRequestEntity paymentRequestEntity = aValidPaymentRequestEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .build();

        paymentRequestDao.persist(paymentRequestEntity);

        assertThat(paymentRequestEntity.getChargeTransaction().getId(), is(notNullValue()));
    }

    @Test
    public void shouldReturnCorrectMaxId() {
        PaymentRequestEntity paymentRequestEntity1 = aValidPaymentRequestEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .build();

        paymentRequestDao.persist(paymentRequestEntity1);
        final PaymentRequestEntity paymentRequestEntity2 = aValidPaymentRequestEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .build();
        paymentRequestDao.persist(paymentRequestEntity2);

        final Long maxId = paymentRequestDao.findMaxId();
        assertThat(maxId, is(paymentRequestEntity2.getId()));
    }

    private void insertTestAccount() {
        this.defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .insert();
    }
}
