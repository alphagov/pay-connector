package uk.gov.pay.connector.it.dao;

import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.dao.ChargeSearchParams;
import uk.gov.pay.connector.dao.PaymentRequestDao;
import uk.gov.pay.connector.dao.TransactionDao;
import uk.gov.pay.connector.model.ServicePaymentReference;
import uk.gov.pay.connector.model.TransactionType;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.PaymentRequestEntity;
import uk.gov.pay.connector.model.domain.RefundStatus;
import uk.gov.pay.connector.model.domain.Transaction;
import uk.gov.pay.connector.model.domain.transaction.TransactionOperation;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.model.TransactionType.REFUND;
import static uk.gov.pay.connector.model.domain.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.model.domain.PaymentRequestEntityFixture.aValidPaymentRequestEntity;
import static uk.gov.pay.connector.model.domain.PaymentRequestEntityFixture.aValidPaymentRequestEntityWithRefund;
import static uk.gov.pay.connector.model.domain.transaction.ChargeTransactionEntityBuilder.aChargeTransactionEntity;

public class TransactionDaoITest extends DaoITestBase {
    private DatabaseFixtures.TestAccount defaultTestAccount;
    private GatewayAccountEntity gatewayAccount;
    private PaymentRequestDao paymentRequestDao;
    private TransactionDao transactionDao;

    @Before
    public void setUp() {
        paymentRequestDao = env.getInstance(PaymentRequestDao.class);
        transactionDao = env.getInstance(TransactionDao.class);

        insertTestAccount();

        gatewayAccount = new GatewayAccountEntity(
                defaultTestAccount.getPaymentProvider(), new HashMap<>(), TEST);
        gatewayAccount.setId(defaultTestAccount.getAccountId());
    }

    @Test
    public void shouldReturnTransactions_byGatewayAccountId() {
        paymentRequestDao.persist(aValidPaymentRequestEntity()
                .withGatewayAccountEntity(gatewayAccount).build());
        paymentRequestDao.persist(aValidPaymentRequestEntity()
                .withGatewayAccountEntity(gatewayAccount).build());

        ChargeSearchParams searchParams = createSearchParams();
        Long expectedId = searchParams.getGatewayAccountId();

        final List<Transaction> entityList = transactionDao.search(searchParams);
        assertThat(entityList.size(), is(2));
        assertThat(entityList.get(0).getGatewayAccountId(), is(expectedId));
        assertThat(entityList.get(1).getGatewayAccountId(), is(expectedId));
    }

    @Test
    public void shouldCountTransactions_byGatewayAccountId() {
        paymentRequestDao.persist(aValidPaymentRequestEntity()
                .withGatewayAccountEntity(gatewayAccount).build());
        paymentRequestDao.persist(aValidPaymentRequestEntity()
                .withGatewayAccountEntity(gatewayAccount).build());

        ChargeSearchParams searchParams = new ChargeSearchParams();
        final Long expectedId = gatewayAccount.getId();
        searchParams.withGatewayAccountId(expectedId);

        final Long total = transactionDao.getTotal(searchParams);
        assertThat(total, is(2L));
    }

    @Test
    public void shouldCountTransactions_GetMoreThanDisplaySize() {
        paymentRequestDao.persist(aValidPaymentRequestEntity()
                .withGatewayAccountEntity(gatewayAccount).build());
        paymentRequestDao.persist(aValidPaymentRequestEntity()
                .withGatewayAccountEntity(gatewayAccount).build());

        ChargeSearchParams searchParams = new ChargeSearchParams();
        final Long expectedId = gatewayAccount.getId();
        searchParams.withGatewayAccountId(expectedId);
        searchParams.withDisplaySize(1L);

        final Long total = transactionDao.getTotal(searchParams);
        assertThat(total, is(2L));
    }

    @Test
    public void shouldCountTransactions_IncludesBeforeCurrentPage() {
        paymentRequestDao.persist(aValidPaymentRequestEntity()
                .withGatewayAccountEntity(gatewayAccount).build());
        paymentRequestDao.persist(aValidPaymentRequestEntity()
                .withGatewayAccountEntity(gatewayAccount).build());

        ChargeSearchParams searchParams = new ChargeSearchParams();
        final Long expectedId = gatewayAccount.getId();
        searchParams.withGatewayAccountId(expectedId);
        searchParams.withDisplaySize(1L);
        searchParams.withPage(2L);

        final Long total = transactionDao.getTotal(searchParams);
        assertThat(total, is(2L));
    }

    @Test
    public void shouldReturnTransactions_byGatewayAccountId_whenMultipleGatewayAccountsExist() {
        final PaymentRequestEntity paymentRequestEntity = aValidPaymentRequestEntity()
                .withGatewayAccountEntity(gatewayAccount).build();
        paymentRequestDao.persist(paymentRequestEntity);

        DatabaseFixtures.TestAccount defaultTestAccount2 = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper).aTestAccount().insert();
        GatewayAccountEntity gatewayAccount2 = new GatewayAccountEntity(
                defaultTestAccount2.getPaymentProvider(), new HashMap<>(), TEST);
        gatewayAccount2.setId(defaultTestAccount2.getAccountId());

        paymentRequestDao.persist(aValidPaymentRequestEntity()
                .withGatewayAccountEntity(gatewayAccount2).build());

        ChargeSearchParams searchParams = createSearchParams();

        assertTransactionByExternalId(paymentRequestEntity.getExternalId(), transactionDao.search(searchParams));
    }

    @Test
    public void shouldReturnTransactions_byEmail_whenMultipleTransactionsWithDifferentEmails() {
        PaymentRequestEntity paymentRequestEntity1 = aValidPaymentRequestEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .build();

        final String expectedEmail = "abc@example.com";
        paymentRequestEntity1.getChargeTransaction().setEmail(expectedEmail);
        paymentRequestDao.persist(paymentRequestEntity1);

        final PaymentRequestEntity paymentRequestEntity2 = aValidPaymentRequestEntity()
                .withGatewayAccountEntity(gatewayAccount).build();
        paymentRequestEntity2.getChargeTransaction().setEmail("zzz@example.com");
        paymentRequestDao.persist(paymentRequestEntity2);

        ChargeSearchParams searchParams = createSearchParams();
        searchParams.withEmailLike(expectedEmail);

        assertTransactionByExternalId(paymentRequestEntity1.getExternalId(), transactionDao.search(searchParams));
    }

    @Test
    public void shouldReturnTransactions_byEmailWithDifferentCasing() {
        PaymentRequestEntity paymentRequestEntity = aValidPaymentRequestEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .build();

        final String expectedEmail = "Abc@example.com";
        paymentRequestEntity.getChargeTransaction().setEmail(expectedEmail);
        paymentRequestDao.persist(paymentRequestEntity);

        ChargeSearchParams searchParams = createSearchParams();
        searchParams.withEmailLike(expectedEmail.toLowerCase());

        assertTransactionByExternalId(paymentRequestEntity.getExternalId(), transactionDao.search(searchParams));
    }

    @Test
    public void shouldReturnTransactions_byEmailWithLowCaseInDb() {
        PaymentRequestEntity paymentRequestEntity1 = aValidPaymentRequestEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .build();

        final String expectedEmail = "abc@example.com";
        paymentRequestEntity1.getChargeTransaction().setEmail(expectedEmail);
        paymentRequestDao.persist(paymentRequestEntity1);

        ChargeSearchParams searchParams = createSearchParams();
        searchParams.withEmailLike(expectedEmail.toUpperCase());

        assertTransactionByExternalId(paymentRequestEntity1.getExternalId(), transactionDao.search(searchParams));
    }

    @Test
    public void shouldReturnTransactions_byEmailWithPartialMatch() {
        PaymentRequestEntity paymentRequestEntity = aValidPaymentRequestEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .build();

        final String expectedEmail = "abc@example.com";
        paymentRequestEntity.getChargeTransaction().setEmail(expectedEmail);
        paymentRequestDao.persist(paymentRequestEntity);

        ChargeSearchParams searchParams = createSearchParams();
        searchParams.withEmailLike("abc");

        assertTransactionByExternalId(paymentRequestEntity.getExternalId(), transactionDao.search(searchParams));
    }

    @Test
    public void shouldReturnRefundTransactions_byEmailWithPartialMatch() {
        PaymentRequestEntity paymentRequestEntity = aValidPaymentRequestEntityWithRefund()
                .withGatewayAccountEntity(gatewayAccount)
                .build();

        final String expectedEmail = "abc@example.com";
        paymentRequestEntity.getChargeTransaction().setEmail(expectedEmail);
        paymentRequestDao.persist(paymentRequestEntity);

        ChargeSearchParams searchParams = createSearchParams();
        searchParams.withEmailLike("abc");
        searchParams.withTransactionType(REFUND);

        assertTransactionByExternalId(paymentRequestEntity.getExternalId(), transactionDao.search(searchParams));
    }

    @Test
    public void shouldReturnTransactions_byEmailWithUnderscore() {
        PaymentRequestEntity paymentRequestEntity1 = aValidPaymentRequestEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .build();

        final String expectedEmail = "a_c@example.com";
        paymentRequestEntity1.getChargeTransaction().setEmail(expectedEmail);
        paymentRequestDao.persist(paymentRequestEntity1);

        final PaymentRequestEntity paymentRequestEntity2 = aValidPaymentRequestEntity()
                .withGatewayAccountEntity(gatewayAccount).build();
        paymentRequestEntity2.getChargeTransaction().setEmail("abc@example.com");
        paymentRequestDao.persist(paymentRequestEntity2);

        ChargeSearchParams searchParams = createSearchParams();
        searchParams.withEmailLike(expectedEmail);

        assertTransactionByExternalId(paymentRequestEntity1.getExternalId(), transactionDao.search(searchParams));
    }

    @Test
    public void shouldReturnTransactions_byEmailWithPercent() {
        PaymentRequestEntity paymentRequestEntity1 = aValidPaymentRequestEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .build();

        final String expectedEmail = "a%c@example.com";
        paymentRequestEntity1.getChargeTransaction().setEmail(expectedEmail);
        paymentRequestDao.persist(paymentRequestEntity1);

        final PaymentRequestEntity paymentRequestEntity2 = aValidPaymentRequestEntity()
                .withGatewayAccountEntity(gatewayAccount).build();
        paymentRequestEntity2.getChargeTransaction().setEmail("abc@example.com");
        paymentRequestDao.persist(paymentRequestEntity2);

        ChargeSearchParams searchParams = createSearchParams();
        searchParams.withEmailLike(expectedEmail);

        assertTransactionByExternalId(paymentRequestEntity1.getExternalId(), transactionDao.search(searchParams));
    }

    @Test
    public void shouldReturnTransactions_byReference() {
        final ServicePaymentReference expectedReference = ServicePaymentReference.of("some random reference");
        PaymentRequestEntity paymentRequestEntity = aValidPaymentRequestEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withReference(expectedReference)
                .build();

        paymentRequestDao.persist(paymentRequestEntity);

        paymentRequestDao.persist(aValidPaymentRequestEntity()
                .withGatewayAccountEntity(gatewayAccount).build());

        ChargeSearchParams searchParams = createSearchParams();
        searchParams.withReferenceLike(expectedReference);

        assertTransactionByExternalId(paymentRequestEntity.getExternalId(), transactionDao.search(searchParams));
    }

    @Test
    public void shouldReturnTransactions_byReferenceWithUnderscore() {
        final ServicePaymentReference expectedReference = ServicePaymentReference.of("a_cedkdkwd");

        PaymentRequestEntity paymentRequestEntity1 = aValidPaymentRequestEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withReference(expectedReference)
                .build();

        paymentRequestDao.persist(paymentRequestEntity1);

        final PaymentRequestEntity paymentRequestEntity2 = aValidPaymentRequestEntity()
                .withGatewayAccountEntity(gatewayAccount).withReference(ServicePaymentReference.of("abcedkdkwd")).build();
        paymentRequestDao.persist(paymentRequestEntity2);

        ChargeSearchParams searchParams = createSearchParams();
        searchParams.withReferenceLike(expectedReference);

        assertTransactionByExternalId(paymentRequestEntity1.getExternalId(), transactionDao.search(searchParams));
    }

    @Test
    public void shouldReturnTransactions_byReferenceWithPercent() {
        final ServicePaymentReference expectedReference = ServicePaymentReference.of("a%cedkdkwd");

        PaymentRequestEntity paymentRequestEntity1 = aValidPaymentRequestEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withReference(expectedReference)
                .build();

        paymentRequestDao.persist(paymentRequestEntity1);

        final PaymentRequestEntity paymentRequestEntity2 = aValidPaymentRequestEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withReference(ServicePaymentReference.of("abcedkdkwd"))
                .build();
        paymentRequestDao.persist(paymentRequestEntity2);

        ChargeSearchParams searchParams = createSearchParams();
        searchParams.withReferenceLike(expectedReference);

        assertTransactionByExternalId(paymentRequestEntity1.getExternalId(), transactionDao.search(searchParams));
    }

    @Test
    public void shouldReturnTransactions_byReferenceWithUppercase() {
        final ServicePaymentReference expectedReference = ServicePaymentReference.of("ABCEdkdkwd");

        PaymentRequestEntity paymentRequestEntity = aValidPaymentRequestEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withReference(expectedReference)
                .build();

        paymentRequestDao.persist(paymentRequestEntity);

        ChargeSearchParams searchParams = createSearchParams();
        searchParams.withReferenceLike(expectedReference);

        assertTransactionByExternalId(paymentRequestEntity.getExternalId(), transactionDao.search(searchParams));
    }

    @Test
    public void shouldReturnTransactions_byReferenceWithUppercaseInRequest() {
        final ServicePaymentReference expectedReference = ServicePaymentReference.of("dkdkwd");

        PaymentRequestEntity paymentRequestEntity = aValidPaymentRequestEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withReference(expectedReference)
                .build();

        paymentRequestDao.persist(paymentRequestEntity);

        ChargeSearchParams searchParams = createSearchParams();
        searchParams.withReferenceLike(ServicePaymentReference.of(expectedReference.toString().toUpperCase()));

        assertTransactionByExternalId(paymentRequestEntity.getExternalId(), transactionDao.search(searchParams));
    }

    @Test
    public void shouldReturnTransactions_byFromDate() {
        final ZonedDateTime paymentDate = ZonedDateTime.now().minusMinutes(10);

        final PaymentRequestEntity paymentRequestEntity = aValidPaymentRequestEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .build();
        paymentRequestEntity.getChargeTransaction().setCreatedDate(paymentDate);
        final String expectedExternalId = paymentRequestEntity.getExternalId();
        paymentRequestDao.persist(paymentRequestEntity);

        final PaymentRequestEntity paymentRequestEntity2 = aValidPaymentRequestEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .build();
        paymentRequestEntity2.getChargeTransaction().setCreatedDate(paymentDate.minusMinutes(5));
        paymentRequestDao.persist(paymentRequestEntity2);

        ChargeSearchParams searchParams = createSearchParams();
        searchParams.withFromDate(paymentDate);

        assertTransactionByExternalId(expectedExternalId, transactionDao.search(searchParams));
    }

    @Test
    public void shouldReturnTransactions_byToDate() {
        final ZonedDateTime paymentDate = ZonedDateTime.now().minusMinutes(10);

        final PaymentRequestEntity paymentRequestEntity = aValidPaymentRequestEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .build();
        paymentRequestEntity.getChargeTransaction().setCreatedDate(paymentDate);
        final String expectedExternalId = paymentRequestEntity.getExternalId();
        paymentRequestDao.persist(paymentRequestEntity);

        final PaymentRequestEntity paymentRequestEntity2 = aValidPaymentRequestEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .build();
        paymentRequestEntity2.getChargeTransaction().setCreatedDate(paymentDate.plusMinutes(5));
        paymentRequestDao.persist(paymentRequestEntity2);

        ChargeSearchParams searchParams = createSearchParams();
        searchParams.withToDate(paymentDate.plusMinutes(1));

        assertTransactionByExternalId(expectedExternalId, transactionDao.search(searchParams));
    }

    @Test
    public void shouldReturnRefundTransactions_byTransactionType() {
        final PaymentRequestEntity paymentRequestEntity = aValidPaymentRequestEntityWithRefund()
                .withGatewayAccountEntity(gatewayAccount)
                .build();
        final String externalId = paymentRequestEntity.getExternalId();
        paymentRequestDao.persist(paymentRequestEntity);

        ChargeSearchParams searchParams = createSearchParams();
        searchParams.withTransactionType(REFUND);

        final List<Transaction> searchResult = transactionDao.search(searchParams);
        assertIsTransactionOperation(searchResult, TransactionOperation.REFUND);
        assertThat(searchResult.get(0).getExternalId(), is(externalId));
    }

    @Test
    public void shouldReturnChargeTransactions_byTransactionType() {
        final PaymentRequestEntity paymentRequestEntity = aValidPaymentRequestEntityWithRefund()
                .withGatewayAccountEntity(gatewayAccount)
                .build();
        paymentRequestDao.persist(paymentRequestEntity);
        final long chargeTransactionId = paymentRequestEntity.getChargeTransaction().getId();

        ChargeSearchParams searchParams = createSearchParams();
        searchParams.withTransactionType(TransactionType.PAYMENT);

        final List<Transaction> searchResult = transactionDao.search(searchParams);
        assertIsTransactionOperation(searchResult, TransactionOperation.CHARGE);
        final long actualChargeTransactionId = searchResult.get(0).getChargeId();
        assertThat(actualChargeTransactionId, is(chargeTransactionId));
    }

    private void assertIsTransactionOperation(List<Transaction> searchResult, TransactionOperation transactionOperation) {
        assertThat(searchResult.size(), is(1));
        assertThat(searchResult.get(0).getTransactionType(), is(transactionOperation.name()));
    }

    @Test
    public void shouldReturnTransactions_byStatus() {
        final ChargeStatus chargeStatus = ChargeStatus.CAPTURED;
        final PaymentRequestEntity paymentRequestEntity =
                persistPaymentRequestForStatus(chargeStatus, RefundStatus.REFUNDED);

        final Long chargeTransactionId = paymentRequestEntity.getChargeTransaction().getId();

        ChargeSearchParams searchParams = createSearchParams();
        searchParams.withExternalState(chargeStatus.toExternal().getStatus());

        assertTransactionById(chargeTransactionId, transactionDao.search(searchParams));
    }

    @Test
    public void shouldReturnTransactions_byChargeStatus() {
        final ChargeStatus chargeStatus = ChargeStatus.CAPTURED;
        final PaymentRequestEntity paymentRequestEntity =
                persistPaymentRequestForStatus(chargeStatus, RefundStatus.REFUNDED);

        final Long chargeTransactionId = paymentRequestEntity.getChargeTransaction().getId();

        ChargeSearchParams searchParams = createSearchParams();
        searchParams.addExternalChargeStates(singletonList(chargeStatus.toExternal().getStatus()));

        assertTransactionById(chargeTransactionId, transactionDao.search(searchParams));
    }

    @Test
    public void shouldReturnTransactions_byRefundStatus() {
        final RefundStatus refundStatus = RefundStatus.REFUNDED;
        final PaymentRequestEntity paymentRequestEntity =
                persistPaymentRequestForStatus(ChargeStatus.CAPTURED, refundStatus);

        final Long refundTransactionId = paymentRequestEntity.getRefundTransactions().get(0).getId();

        ChargeSearchParams searchParams = createSearchParams();
        searchParams.addExternalRefundStates(singletonList(refundStatus.toExternal().getStatus()));

        assertTransactionById(refundTransactionId, transactionDao.search(searchParams));
    }

    @Test
    public void shouldReturnTransactions_byMultipleStatues() {
        final ChargeStatus chargeStatus = ChargeStatus.CAPTURED;
        final RefundStatus refundStatus = RefundStatus.REFUNDED;
        final PaymentRequestEntity paymentRequestEntity =
                persistPaymentRequestForStatus(chargeStatus, refundStatus);

        final Long chargeTransactionId = paymentRequestEntity.getChargeTransaction().getId();
        final Long refundTransactionId = paymentRequestEntity.getRefundTransactions().get(0).getId();

        ChargeSearchParams searchParams = createSearchParams();
        searchParams.addExternalChargeStates(singletonList(
                chargeStatus.toExternal().getStatus()
        ));
        searchParams.addExternalRefundStates(singletonList(
                refundStatus.toExternal().getStatus())
        );

        final List<Transaction> searchResult = transactionDao.search(searchParams);
        assertThat(searchResult.size(), is(2));
        assertThat(searchResult.get(0).getChargeId(), is(refundTransactionId));
        assertThat(searchResult.get(1).getChargeId(), is(chargeTransactionId));
    }

    @Test
    public void shouldReturnTransactions_byDisplaySize() {
        final PaymentRequestEntity paymentRequestEntity = aValidPaymentRequestEntityWithRefund()
                .withGatewayAccountEntity(gatewayAccount).build();
        paymentRequestDao.persist(paymentRequestEntity);
        paymentRequestDao.persist(aValidPaymentRequestEntityWithRefund()
                .withGatewayAccountEntity(gatewayAccount).build());
        paymentRequestDao.persist(aValidPaymentRequestEntityWithRefund()
                .withGatewayAccountEntity(gatewayAccount).build());

        ChargeSearchParams searchParams = createSearchParams();
        searchParams.withPage(2L);
        searchParams.withDisplaySize(4L);

        final List<Transaction> searchResult = transactionDao.search(searchParams);
        assertThat(searchResult.size(), is(2));
        final Long refundId = paymentRequestEntity.getRefundTransactions().get(0).getId();
        assertThat(searchResult.get(0).getChargeId(), is(refundId));
        final Long chargeId = paymentRequestEntity.getChargeTransaction().getId();
        assertThat(searchResult.get(1).getChargeId(), is(chargeId));
    }
    

    private ChargeSearchParams createSearchParams() {
        ChargeSearchParams searchParams = new ChargeSearchParams();
        searchParams.withGatewayAccountId(gatewayAccount.getId());
        searchParams.withPage(1L);
        searchParams.withDisplaySize(500L);
        return searchParams;
    }

    private PaymentRequestEntity persistPaymentRequestForStatus(ChargeStatus chargeStatus, RefundStatus refundStatus) {
        PaymentRequestEntity paymentRequestEntity = aValidPaymentRequestEntityWithRefund()
                .withGatewayAccountEntity(gatewayAccount)
                .build();

        paymentRequestEntity.getChargeTransaction().updateStatus(chargeStatus);
        paymentRequestEntity.getRefundTransactions().get(0).updateStatus(refundStatus);
        paymentRequestDao.persist(paymentRequestEntity);

        return paymentRequestEntity;
    }

    private PaymentRequestEntity persistPaymentRequestForCardBrand(String cardBrand) {
        PaymentRequestEntity paymentRequestEntity = aValidPaymentRequestEntityWithRefund()
                .withGatewayAccountEntity(gatewayAccount)
                .build();
       
        paymentRequestDao.persist(paymentRequestEntity);

        return paymentRequestEntity;
    }

    private void assertTransactionByExternalId(String expectedExternalId, List<Transaction> searchResult) {
        assertThat(searchResult.size(), is(1));
        final String actualExternalId = searchResult.get(0).getExternalId();
        assertThat(actualExternalId, is(expectedExternalId));
    }

    private void assertTransactionById(Long expectedId, List<Transaction> searchResult) {
        assertThat(searchResult.size(), is(1));
        assertThat(searchResult.get(0).getChargeId(), is(expectedId));
    }

    private void insertTestAccount() {
        this.defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .insert();
    }
}
