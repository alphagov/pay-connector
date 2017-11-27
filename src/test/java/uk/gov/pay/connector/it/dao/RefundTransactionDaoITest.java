package uk.gov.pay.connector.it.dao;

import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.dao.PaymentRequestDao;
import uk.gov.pay.connector.dao.RefundTransactionDao;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.PaymentRequestEntity;
import uk.gov.pay.connector.model.domain.transaction.RefundTransactionEntity;
import uk.gov.pay.connector.service.PaymentGatewayName;

import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import static uk.gov.pay.connector.model.domain.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.model.domain.PaymentRequestEntityFixture.aValidPaymentRequestEntity;
import static uk.gov.pay.connector.model.domain.transaction.ChargeTransactionEntityBuilder.aChargeTransactionEntity;
import static uk.gov.pay.connector.model.domain.transaction.RefundTransactionEntityBuilder.aRefundTransactionEntity;

public class RefundTransactionDaoITest extends DaoITestBase {

    private PaymentRequestDao paymentRequestDao;
    private RefundTransactionDao refundTransactionDao;
    private GatewayAccountEntity gatewayAccount;
    private DatabaseFixtures.TestAccount defaultTestAccount;

    @Before
    public void setUp() throws Exception {
        paymentRequestDao = env.getInstance(PaymentRequestDao.class);
        refundTransactionDao = env.getInstance(RefundTransactionDao.class);

        this.defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .insert();

        gatewayAccount = new GatewayAccountEntity(defaultTestAccount.getPaymentProvider(), new HashMap<>(), TEST);
        gatewayAccount.setId(defaultTestAccount.getAccountId());
    }

    @Test
    public void canFindRefundByProviderAndReference() throws Exception {
        String refundReference = UUID.randomUUID().toString();
        RefundTransactionEntity refundTransactionEntity = aRefundTransactionEntity()
                .withRefundReference(refundReference)
                .build();
        PaymentRequestEntity paymentRequestEntity = aValidPaymentRequestEntity()
                .withTransactions(aChargeTransactionEntity().build(), refundTransactionEntity)
                .withGatewayAccountEntity(gatewayAccount)
                .build();
        paymentRequestDao.persist(paymentRequestEntity);

        PaymentGatewayName provider = PaymentGatewayName.valueFrom(gatewayAccount.getGatewayName());
        Optional<RefundTransactionEntity> loadedRefundTransactionEntityOptional =
                refundTransactionDao.findByProviderAndReference(provider, refundReference);

        assertThat(loadedRefundTransactionEntityOptional.isPresent(), is(true));
        assertThat(loadedRefundTransactionEntityOptional.get().getRefundReference(), is(refundReference));
    }

    @Test
    public void cannotFindRefundThatDoesNotExist() throws Exception {
        String refundReferenceThatDoesNotExist = "doesNotExist";
        PaymentGatewayName provider = PaymentGatewayName.valueFrom(gatewayAccount.getGatewayName());
        Optional<RefundTransactionEntity> loadedRefundTransactionEntityOptional =
                refundTransactionDao
                        .findByProviderAndReference(provider, refundReferenceThatDoesNotExist);

        assertThat(loadedRefundTransactionEntityOptional.isPresent(), is(false));
    }

    @Test
    public void cannotFindRefundWithRefundReferenceButIncorrectGatewayAccount() throws Exception {
        String refundReference = UUID.randomUUID().toString();
        RefundTransactionEntity refundTransactionEntity = aRefundTransactionEntity()
                .withRefundReference(refundReference)
                .build();
        PaymentRequestEntity paymentRequestEntity = aValidPaymentRequestEntity()
                .withTransactions(aChargeTransactionEntity().build(), refundTransactionEntity)
                .withGatewayAccountEntity(gatewayAccount)
                .build();
        paymentRequestDao.persist(paymentRequestEntity);

        Optional<RefundTransactionEntity> loadedRefundTransactionEntityOptional =
                refundTransactionDao.findByProviderAndReference(PaymentGatewayName.WORLDPAY, refundReference);

        assertThat(loadedRefundTransactionEntityOptional.isPresent(), is(false));
    }

    @Test
    public void canFindRefundByExternalReference() throws Exception {
        String refundExternalId = UUID.randomUUID().toString().substring(0, 10);
        RefundTransactionEntity refundTransactionEntity = aRefundTransactionEntity()
                .withRefundExternalId(refundExternalId)
                .build();
        PaymentRequestEntity paymentRequestEntity = aValidPaymentRequestEntity()
                .withTransactions(aChargeTransactionEntity().build(), refundTransactionEntity)
                .withGatewayAccountEntity(gatewayAccount)
                .build();
        paymentRequestDao.persist(paymentRequestEntity);

        Optional<RefundTransactionEntity> loadedRefundTransaction = refundTransactionDao.findByExternalId(refundExternalId);

        assertThat(loadedRefundTransaction.isPresent(), is(true));
        assertThat(loadedRefundTransaction.get().getRefundExternalId(), is(refundExternalId));
    }
}
