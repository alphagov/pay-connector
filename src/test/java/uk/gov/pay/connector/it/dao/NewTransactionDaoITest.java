package uk.gov.pay.connector.it.dao;

import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.dao.ChargeSearchParams;
import uk.gov.pay.connector.dao.NewTransactionDao;
import uk.gov.pay.connector.dao.PaymentRequestDao;
import uk.gov.pay.connector.model.TransactionType;
import uk.gov.pay.connector.model.domain.CardEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.PaymentRequestEntity;
import uk.gov.pay.connector.model.domain.RefundStatus;
import uk.gov.pay.connector.model.domain.transaction.RefundTransactionEntity;
import uk.gov.pay.connector.model.domain.transaction.TransactionEntity;
import uk.gov.pay.connector.model.domain.transaction.TransactionOperation;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.model.TransactionType.REFUND;
import static uk.gov.pay.connector.model.domain.CardEntityBuilder.aCardEntity;
import static uk.gov.pay.connector.model.domain.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.model.domain.PaymentRequestEntityFixture.aValidPaymentRequestEntity;
import static uk.gov.pay.connector.model.domain.PaymentRequestEntityFixture.aValidPaymentRequestEntityWithRefund;
import static uk.gov.pay.connector.model.domain.transaction.ChargeTransactionEntityBuilder.aChargeTransactionEntity;

public class NewTransactionDaoITest extends DaoITestBase {
    private DatabaseFixtures.TestAccount defaultTestAccount;
    private GatewayAccountEntity gatewayAccount;
    private PaymentRequestDao paymentRequestDao;
    private NewTransactionDao transactionDao;

    @Before
    public void setUp() {
        paymentRequestDao = env.getInstance(PaymentRequestDao.class);
        transactionDao = env.getInstance(NewTransactionDao.class);

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

        ChargeSearchParams searchParams = new ChargeSearchParams();
        final Long expectedId = gatewayAccount.getId();
        searchParams.withGatewayAccountId(expectedId);

        final List<TransactionEntity> entityList = transactionDao.search(searchParams);
        assertThat(entityList.size(), is(2));
        assertThat(entityList.get(0).getPaymentRequest().getGatewayAccount().getId(), is(expectedId));
        assertThat(entityList.get(1).getPaymentRequest().getGatewayAccount().getId(), is(expectedId));
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

        ChargeSearchParams searchParams = new ChargeSearchParams();
        final Long expectedId = gatewayAccount.getId();
        searchParams.withGatewayAccountId(expectedId);

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
        final String expectedReference = "some random reference";
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
        final String expectedReference = "a_cedkdkwd";

        PaymentRequestEntity paymentRequestEntity1 = aValidPaymentRequestEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withReference(expectedReference)
                .build();

        paymentRequestDao.persist(paymentRequestEntity1);

        final PaymentRequestEntity paymentRequestEntity2 = aValidPaymentRequestEntity()
                .withGatewayAccountEntity(gatewayAccount).withReference("abcedkdkwd").build();
        paymentRequestDao.persist(paymentRequestEntity2);

        ChargeSearchParams searchParams = createSearchParams();
        searchParams.withReferenceLike(expectedReference);

        assertTransactionByExternalId(paymentRequestEntity1.getExternalId(), transactionDao.search(searchParams));
    }

    @Test
    public void shouldReturnTransactions_byReferenceWithPercent() {
        final String expectedReference = "a%cedkdkwd";

        PaymentRequestEntity paymentRequestEntity1 = aValidPaymentRequestEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withReference(expectedReference)
                .build();

        paymentRequestDao.persist(paymentRequestEntity1);

        final PaymentRequestEntity paymentRequestEntity2 = aValidPaymentRequestEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withReference("abcedkdkwd")
                .build();
        paymentRequestDao.persist(paymentRequestEntity2);

        ChargeSearchParams searchParams = createSearchParams();
        searchParams.withReferenceLike(expectedReference);

        assertTransactionByExternalId(paymentRequestEntity1.getExternalId(), transactionDao.search(searchParams));
    }

    @Test
    public void shouldReturnTransactions_byReferenceWithUppercase() {
        final String expectedReference = "ABCEdkdkwd";

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
        final String expectedReference = "dkdkwd";

        PaymentRequestEntity paymentRequestEntity = aValidPaymentRequestEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withReference(expectedReference)
                .build();

        paymentRequestDao.persist(paymentRequestEntity);

        ChargeSearchParams searchParams = createSearchParams();
        searchParams.withReferenceLike(expectedReference.toUpperCase());

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
        final String expectedExternalId = paymentRequestEntity.getRefundTransactions().get(0).getRefundExternalId();
        paymentRequestDao.persist(paymentRequestEntity);

        ChargeSearchParams searchParams = createSearchParams();
        searchParams.withTransactionType(REFUND);

        final List<TransactionEntity> searchResult = transactionDao.search(searchParams);
        assertIsTransactionOperation(searchResult, TransactionOperation.REFUND);
        final String actualExternalId = ((RefundTransactionEntity) searchResult.get(0)).getRefundExternalId();
        assertThat(actualExternalId, is(expectedExternalId));
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

        final List<TransactionEntity> searchResult = transactionDao.search(searchParams);
        assertIsTransactionOperation(searchResult, TransactionOperation.CHARGE);
        final long actualChargeTransactionId = searchResult.get(0).getId();
        assertThat(actualChargeTransactionId, is(chargeTransactionId));
    }

    private void assertIsTransactionOperation(List<TransactionEntity> searchResult, TransactionOperation charge) {
        assertThat(searchResult.size(), is(1));
        assertThat(searchResult.get(0).getOperation(), is(charge));
    }

    @Test
    public void shouldReturnTransactions_byCardBrand() {
        final String cardBrand = "visa";
        final PaymentRequestEntity paymentRequestEntity = persistPaymentRequestForCardBrand(cardBrand);
        persistPaymentRequestForCardBrand("mastercard");

        final Long chargeTransactionId = paymentRequestEntity.getChargeTransaction().getId();
        final Long refundTransactionId = paymentRequestEntity.getRefundTransactions().get(0).getId();

        ChargeSearchParams searchParams = createSearchParams();
        searchParams.withCardBrand(cardBrand);

        final List<TransactionEntity> searchResult = transactionDao.search(searchParams);
        assertThat(searchResult.size(), is(2));
        assertThat(searchResult.get(0).getId(), is(refundTransactionId));
        assertThat(searchResult.get(1).getId(), is(chargeTransactionId));
    }

    @Test
    public void shouldReturnTransactions_byMultipleCardBrand() {
        final String visaCardBrand = "visa";
        final PaymentRequestEntity visaPaymentRequestEntity = persistPaymentRequestForCardBrand(visaCardBrand);
        final String mastercardCardBrand = "master-card";
        final PaymentRequestEntity mastercardPaymentRequestEntity = persistPaymentRequestForCardBrand(mastercardCardBrand);
        persistPaymentRequestForCardBrand("anotherCardBrand");

        final Long visaChargeTransactionId = visaPaymentRequestEntity.getChargeTransaction().getId();
        final Long visaRefundTransactionId = visaPaymentRequestEntity.getRefundTransactions().get(0).getId();
        final Long mastercardChargeTransactionId = mastercardPaymentRequestEntity.getChargeTransaction().getId();
        final Long mastercardRefundTransactionId = mastercardPaymentRequestEntity.getRefundTransactions().get(0).getId();

        ChargeSearchParams searchParams = createSearchParams();
        searchParams.withCardBrands(asList(visaCardBrand, mastercardCardBrand));

        final List<TransactionEntity> searchResult = transactionDao.search(searchParams);
        assertThat(searchResult.size(), is(4));
        assertThat(searchResult.get(0).getId(), is(mastercardRefundTransactionId));
        assertThat(searchResult.get(1).getId(), is(mastercardChargeTransactionId));
        assertThat(searchResult.get(2).getId(), is(visaRefundTransactionId));
        assertThat(searchResult.get(3).getId(), is(visaChargeTransactionId));
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

        final List<TransactionEntity> searchResult = transactionDao.search(searchParams);
        assertThat(searchResult.size(), is(2));
        assertThat(searchResult.get(0).getId(), is(refundTransactionId));
        assertThat(searchResult.get(1).getId(), is(chargeTransactionId));
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

        final List<TransactionEntity> searchResult = transactionDao.search(searchParams);
        assertThat(searchResult.size(), is(2));
        final Long refundId = paymentRequestEntity.getRefundTransactions().get(0).getId();
        assertThat(searchResult.get(0).getId(), is(refundId));
        final Long chargeId = paymentRequestEntity.getChargeTransaction().getId();
        assertThat(searchResult.get(1).getId(), is(chargeId));
    }

    @Test
    public void shouldReturnTransactions_AllParametersSet() throws Exception {
        String ref = "ref1";
        String email = "foo@foo.com";
        String cardBrand = "visa";

        ZonedDateTime createdTime = ZonedDateTime.now();
        PaymentRequestEntity paymentRequestEntity = aValidPaymentRequestEntity()
                .withReference(ref)
                .withTransactions(aChargeTransactionEntity()
                        .withEmail(email)
                        .withCard(aCardEntity()
                                .withCardBrand(cardBrand)
                                .build())
                        .withCreatedDate(createdTime)
                        .withStatus(ChargeStatus.CREATED)
                        .build())
                .withGatewayAccountEntity(gatewayAccount).build();
        paymentRequestDao.persist(paymentRequestEntity);
        paymentRequestDao.persist(aValidPaymentRequestEntity()
                .withGatewayAccountEntity(gatewayAccount).build());

        ChargeSearchParams searchParams = new ChargeSearchParams();
        final Long expectedId = gatewayAccount.getId();
        searchParams.withGatewayAccountId(expectedId);
        searchParams.withReferenceLike(ref);
        searchParams.withEmailLike(email);
        searchParams.withCardBrand(cardBrand);
        searchParams.withFromDate(createdTime.minusSeconds(5));
        searchParams.withToDate(createdTime.plusSeconds(5));
        searchParams.withTransactionType(TransactionType.PAYMENT);
        searchParams.addExternalChargeStates(singletonList(ChargeStatus.CREATED.toExternal().getStatus()));
        searchParams.addExternalRefundStates(singletonList(RefundStatus.CREATED.toExternal().getStatus()));

        final List<TransactionEntity> searchResult = transactionDao.search(searchParams);
        assertThat(searchResult.size(), is(1));
        final Long refundId = paymentRequestEntity.getChargeTransaction().getId();
        assertThat(searchResult.get(0).getId(), is(refundId));
    }

    @Test
    public void shouldCountTransactions_AllParametersSet() throws Exception {
        String ref = "ref1";
        String email = "foo@foo.com";
        String cardBrand = "visa";

        ZonedDateTime createdTime = ZonedDateTime.now();
        paymentRequestDao.persist(aValidPaymentRequestEntity()
                .withReference(ref)
                .withTransactions(aChargeTransactionEntity()
                        .withEmail(email)
                        .withCard(aCardEntity()
                                .withCardBrand(cardBrand)
                                .build())
                        .withCreatedDate(createdTime)
                        .withStatus(ChargeStatus.CREATED)
                        .build())
                .withGatewayAccountEntity(gatewayAccount).build());
        paymentRequestDao.persist(aValidPaymentRequestEntity()
                .withGatewayAccountEntity(gatewayAccount).build());

        ChargeSearchParams searchParams = new ChargeSearchParams();
        final Long expectedId = gatewayAccount.getId();
        searchParams.withGatewayAccountId(expectedId);
        searchParams.withReferenceLike(ref);
        searchParams.withEmailLike(email);
        searchParams.withCardBrand(cardBrand);
        searchParams.withFromDate(createdTime.minusSeconds(5));
        searchParams.withToDate(createdTime.plusSeconds(5));
        searchParams.withTransactionType(TransactionType.PAYMENT);
        searchParams.addExternalChargeStates(singletonList(ChargeStatus.CREATED.toExternal().getStatus()));
        searchParams.addExternalRefundStates(singletonList(RefundStatus.CREATED.toExternal().getStatus()));

        final Long total = transactionDao.getTotal(searchParams);
        assertThat(total, is(1L));
    }

    private ChargeSearchParams createSearchParams() {
        ChargeSearchParams searchParams = new ChargeSearchParams();
        searchParams.withGatewayAccountId(gatewayAccount.getId());
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
        final CardEntity card = aCardEntity()
                .withCardBrand(cardBrand)
                .build();
        paymentRequestEntity.getChargeTransaction().setCard(card);
        paymentRequestDao.persist(paymentRequestEntity);

        return paymentRequestEntity;
    }

    private void assertTransactionByExternalId(String expectedExternalId, List<TransactionEntity> searchResult) {
        assertThat(searchResult.size(), is(1));
        final String actualExternalId = searchResult.get(0).getPaymentRequest().getExternalId();
        assertThat(actualExternalId, is(expectedExternalId));
    }

    private void assertTransactionById(Long expectedId, List<TransactionEntity> searchResult) {
        assertThat(searchResult.size(), is(1));
        assertThat(searchResult.get(0).getId(), is(expectedId));
    }

    private void insertTestAccount() {
        this.defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .insert();
    }
}
