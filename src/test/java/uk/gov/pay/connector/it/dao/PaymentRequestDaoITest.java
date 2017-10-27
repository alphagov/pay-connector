package uk.gov.pay.connector.it.dao;

import org.hamcrest.core.IsNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import uk.gov.pay.connector.dao.PaymentRequestDao;
import uk.gov.pay.connector.exception.InvalidStateTransitionException;
import uk.gov.pay.connector.model.domain.*;

import java.util.HashMap;
import java.util.Optional;

import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static uk.gov.pay.connector.model.domain.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.model.domain.PaymentRequestEntityFixture.aValidPaymentRequestEntity;
import static uk.gov.pay.connector.model.domain.TransactionEntityBuilder.aTransactionEntity;

public class PaymentRequestDaoITest extends DaoITestBase {
    private DatabaseFixtures.TestAccount defaultTestAccount;
    private PaymentRequestDao paymentRequestDao;
    @Rule
    public ExpectedException expectedEx = ExpectedException.none();
    private GatewayAccountEntity gatewayAccount;


    @Before
    public void setUp() throws Exception {
        paymentRequestDao = env.getInstance(PaymentRequestDao.class);
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
    public void shouldUpdateTransactionStatus() throws Exception {
        PaymentRequestEntity paymentRequestEntity = aValidPaymentRequestEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .build();

        paymentRequestDao.persist(paymentRequestEntity);

        paymentRequestDao.updateChargeTransactionStatus(paymentRequestEntity.getExternalId(),
                ChargeStatus.ENTERING_CARD_DETAILS);
        final Optional<PaymentRequestEntity> byExternalId =
                paymentRequestDao.findByExternalId(paymentRequestEntity.getExternalId());

        assertThat(byExternalId.get().getTransactions().get(0).getStatus(), is(ChargeStatus.ENTERING_CARD_DETAILS));
    }

    @Test(expected = InvalidStateTransitionException.class)
    public void shouldThrowException_whenUpdatingToInvalidState() throws Exception {
        PaymentRequestEntity paymentRequestEntity = aValidPaymentRequestEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .build();

        paymentRequestDao.persist(paymentRequestEntity);

        paymentRequestDao.updateChargeTransactionStatus(paymentRequestEntity.getExternalId(),
                 ChargeStatus.AUTHORISATION_READY);
    }

    @Test
    public void shouldPersistMultipleTransactions() throws Exception {
        PaymentRequestEntity paymentRequestEntity = aValidPaymentRequestEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withTransactions(asList(aTransactionEntity().build(), aTransactionEntity().build())).build();

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

        assertThat(paymentRequestEntity.getChargeTransaction().getId(), is(IsNull.notNullValue()));
    }

    private void insertTestAccount() {
        this.defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .insert();
    }
}
