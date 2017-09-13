package uk.gov.pay.connector.it.dao;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import uk.gov.pay.connector.dao.ChargeSearchParams;
import uk.gov.pay.connector.dao.TransactionDao;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.RefundStatus;
import uk.gov.pay.connector.model.domain.Transaction;
import uk.gov.pay.connector.util.DateTimeUtils;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static java.time.ZonedDateTime.now;
import static org.exparity.hamcrest.date.ZonedDateTimeMatchers.within;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.it.dao.DatabaseFixtures.withDatabaseTestHelper;
import static uk.gov.pay.connector.model.api.ExternalChargeState.EXTERNAL_CREATED;
import static uk.gov.pay.connector.model.api.ExternalChargeState.EXTERNAL_STARTED;
import static uk.gov.pay.connector.model.api.ExternalRefundStatus.EXTERNAL_SUBMITTED;
import static uk.gov.pay.connector.model.api.ExternalRefundStatus.EXTERNAL_SUCCESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_READY;
import static uk.gov.pay.connector.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.model.domain.RefundStatus.CREATED;
import static uk.gov.pay.connector.model.domain.RefundStatus.REFUNDED;

public class TransactionDaoITest extends DaoITestBase {

    private static final String FROM_DATE = "2016-01-01T01:00:00Z";
    private static final String TO_DATE = "2026-01-08T01:00:00Z";
    private static final String DEFAULT_TEST_CHARGE_DESCRIPTION = "Charge description";
    private static final String DEFAULT_TEST_CHARGE_REFERENCE = "Council tax thing";

    private TransactionDao transactionDao;
    private DatabaseFixtures.TestAccount defaultTestAccount;

    @Before
    public void setupAccountWithOneChargeAndRefundForTheCharge() {
        transactionDao = env.getInstance(TransactionDao.class);

        defaultTestAccount =
                withDatabaseTestHelper(databaseTestHelper)
                        .aTestAccount()
                        .insert();
    }

    @Test
    public void searchChargesByGatewayAccountIdOnly() throws Exception {

        // given
        DatabaseFixtures.TestCharge testCharge = insertNewChargeWithId(1L, now());
        DatabaseFixtures.TestCardDetails testCardDetails = updateCardDetailsForCharge(testCharge);
        DatabaseFixtures.TestRefund testRefund = insertNewRefundForCharge(testCharge, 2L, now().plusSeconds(1));

        ChargeSearchParams params = new ChargeSearchParams();

        // when
        List<Transaction> transactions = transactionDao.findAllBy(defaultTestAccount.getAccountId(), params);

        // then
        assertThat(transactions.size(), is(2));

        Transaction transactionRefund = transactions.get(0);
        assertThat(transactionRefund.getTransactionType(), is("refund"));
        assertThat(transactionRefund.getChargeId(), is(testCharge.getChargeId()));
        assertThat(transactionRefund.getAmount(), is(testRefund.getAmount()));
        assertThat(transactionRefund.getReference(), is(testCharge.getReference()));
        assertThat(transactionRefund.getDescription(), is(testCharge.getDescription()));
        assertThat(transactionRefund.getStatus(), is(testRefund.getStatus().toString()));
        assertThat(transactionRefund.getCardBrand(), is(testCardDetails.getCardBrand()));
        assertDateMatch(transactionRefund.getCreatedDate().toString());

        Transaction transactionCharge = transactions.get(1);
        assertThat(transactionCharge.getTransactionType(), is("charge"));
        assertThat(transactionCharge.getChargeId(), is(testCharge.getChargeId()));
        assertThat(transactionCharge.getAmount(), is(testCharge.getAmount()));
        assertThat(transactionCharge.getReference(), is(testCharge.getReference()));
        assertThat(transactionCharge.getDescription(), is(testCharge.getDescription()));
        assertThat(transactionCharge.getStatus(), is(testCharge.getChargeStatus().toString()));
        assertThat(transactionCharge.getCardBrand(), is(testCardDetails.getCardBrand()));
        assertDateMatch(transactionCharge.getCreatedDate().toString());
    }

    @Test
    public void searchChargesByFullEmailMatch() throws Exception {

        // given
        DatabaseFixtures.TestCharge testCharge = insertNewChargeWithId(1L, now().plusHours(1));
        DatabaseFixtures.TestRefund testRefund = insertNewRefundForCharge(testCharge, 2L, now().plusHours(2));
        ChargeSearchParams params = new ChargeSearchParams()
                .withEmailLike(testCharge.getEmail());

        // when
        List<Transaction> transactions = transactionDao.findAllBy(defaultTestAccount.getAccountId(), params);

        // then
        assertThat(transactions.size(), is(2));

        assertThat(transactions.get(0).getAmount(), is(testRefund.getAmount()));
        assertThat(transactions.get(0).getTransactionType(), is("refund"));
        assertThat(transactions.get(1).getAmount(), is(testCharge.getAmount()));
        assertThat(transactions.get(1).getTransactionType(), is("charge"));
    }

    @Test
    public void searchChargesByPartialEmailMatch() throws Exception {

        // given
        DatabaseFixtures.TestCharge testCharge = insertNewChargeWithId(1L, now().plusHours(1));
        DatabaseFixtures.TestRefund testRefund = insertNewRefundForCharge(testCharge, 2L, now().plusHours(2));
        ChargeSearchParams params = new ChargeSearchParams()
                .withEmailLike("alice");

        // when
        List<Transaction> transactions = transactionDao.findAllBy(defaultTestAccount.getAccountId(), params);

        // then
        assertThat(transactions.size(), is(2));

        assertThat(transactions.get(0).getAmount(), is(testRefund.getAmount()));
        assertThat(transactions.get(0).getTransactionType(), is("refund"));
        assertThat(transactions.get(1).getAmount(), is(testCharge.getAmount()));
        assertThat(transactions.get(1).getTransactionType(), is("charge"));
    }

    @Test
    public void searchChargesByCardBrandOnly() throws Exception {

        // given
        DatabaseFixtures.TestCharge testCharge = insertNewChargeWithId(1L, now().plusHours(1));
        DatabaseFixtures.TestCardDetails testCardDetails = updateCardDetailsForCharge(testCharge);
        DatabaseFixtures.TestRefund testRefund = insertNewRefundForCharge(testCharge, 2L, now().plusHours(2));
        ChargeSearchParams params = new ChargeSearchParams()
                .withCardBrand(testCardDetails.getCardBrand());

        // when
        List<Transaction> transactions = transactionDao.findAllBy(defaultTestAccount.getAccountId(), params);

        // then
        assertThat(transactions.size(), is(2));

        assertThat(transactions.get(0).getAmount(), is(testRefund.getAmount()));
        assertThat(transactions.get(0).getTransactionType(), is("refund"));
        assertThat(transactions.get(1).getAmount(), is(testCharge.getAmount()));
        assertThat(transactions.get(1).getTransactionType(), is("charge"));
    }

    @Test
    public void searchChargesWithDefaultSizeAndPage_shouldGetChargesInCreationDateOrder() throws Exception {

        // given
        DatabaseFixtures.TestCharge charge1 = insertNewChargeWithId(1L, now().plusHours(1));
        insertNewRefundForCharge(charge1, 2L, now().plusHours(2));
        DatabaseFixtures.TestCharge charge2 = insertNewChargeWithId(3L, now().plusHours(3));
        insertNewRefundForCharge(charge2, 4L, now().plusHours(4));
        insertNewRefundForCharge(charge2, 5L, now().plusHours(5));
        DatabaseFixtures.TestCharge charge3 = insertNewChargeWithId(6L, now().plusHours(6));

        ChargeSearchParams params = new ChargeSearchParams();

        // when
        List<Transaction> transactions = transactionDao.findAllBy(defaultTestAccount.getAccountId(), params);

        // then
        assertThat(transactions.size(), is(6));
        assertThat(transactions.get(0).getExternalId(), is(charge3.getExternalChargeId()));
        assertThat(transactions.get(0).getTransactionType(), is("charge"));
        assertThat(transactions.get(0).getAmount(), is(6L));

        assertThat(transactions.get(1).getExternalId(), is(charge2.getExternalChargeId()));
        assertThat(transactions.get(1).getTransactionType(), is("refund"));
        assertThat(transactions.get(1).getAmount(), is(5L));

        assertThat(transactions.get(2).getExternalId(), is(charge2.getExternalChargeId()));
        assertThat(transactions.get(2).getTransactionType(), is("refund"));
        assertThat(transactions.get(2).getAmount(), is(4L));

        assertThat(transactions.get(3).getExternalId(), is(charge2.getExternalChargeId()));
        assertThat(transactions.get(3).getTransactionType(), is("charge"));
        assertThat(transactions.get(3).getAmount(), is(3L));

        assertThat(transactions.get(4).getExternalId(), is(charge1.getExternalChargeId()));
        assertThat(transactions.get(4).getTransactionType(), is("refund"));
        assertThat(transactions.get(4).getAmount(), is(2L));

        assertThat(transactions.get(5).getExternalId(), is(charge1.getExternalChargeId()));
        assertThat(transactions.get(5).getTransactionType(), is("charge"));
        assertThat(transactions.get(5).getAmount(), is(1L));
    }

    @Test
    public void searchChargesWithSizeAndPageSetShouldGetTransactionsInCreationDateOrder() throws Exception {

        // given
        DatabaseFixtures.TestCharge charge1 = insertNewChargeWithId(1L, now().plusHours(1));
        insertNewRefundForCharge(charge1, 2L, now().plusHours(2));
        DatabaseFixtures.TestCharge charge2 = insertNewChargeWithId(3L, now().plusHours(3));
        insertNewRefundForCharge(charge2, 4L, now().plusHours(4));
        insertNewRefundForCharge(charge2, 5L, now().plusHours(5));
        DatabaseFixtures.TestCharge charge3 = insertNewChargeWithId(6L, now().plusHours(6));
        DatabaseFixtures.TestCharge charge4 = insertNewChargeWithId(6L, now().plusHours(7));
        insertNewRefundForCharge(charge3, 5L, now().plusHours(8));

        // when
        ChargeSearchParams params = new ChargeSearchParams()
                .withPage(1L)
                .withDisplaySize(3L);

        List<Transaction> transactions = transactionDao.findAllBy(defaultTestAccount.getAccountId(), params);
        // then
        assertThat(transactions.size(), is(3));

        assertThat(transactions.get(0).getExternalId(), is(charge3.getExternalChargeId()));
        assertThat(transactions.get(0).getTransactionType(), is("refund"));

        assertThat(transactions.get(1).getExternalId(), is(charge4.getExternalChargeId()));
        assertThat(transactions.get(1).getTransactionType(), is("charge"));

        assertThat(transactions.get(2).getExternalId(), is(charge3.getExternalChargeId()));
        assertThat(transactions.get(2).getTransactionType(), is("charge"));

        // when
        params = new ChargeSearchParams()
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withPage(2L)
                .withDisplaySize(3L);

        transactions = transactionDao.findAllBy(defaultTestAccount.getAccountId(), params);

        // then
        assertThat(transactions.size(), is(3));

        assertThat(transactions.get(0).getExternalId(), is(charge2.getExternalChargeId()));
        assertThat(transactions.get(0).getTransactionType(), is("refund"));
        assertThat(transactions.get(0).getAmount(), is(5L));

        assertThat(transactions.get(1).getExternalId(), is(charge2.getExternalChargeId()));
        assertThat(transactions.get(1).getTransactionType(), is("refund"));
        assertThat(transactions.get(1).getAmount(), is(4L));

        assertThat(transactions.get(2).getExternalId(), is(charge2.getExternalChargeId()));
        assertThat(transactions.get(2).getTransactionType(), is("charge"));

        // when
        params = new ChargeSearchParams()
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withPage(3L)
                .withDisplaySize(3L);

        transactions = transactionDao.findAllBy(defaultTestAccount.getAccountId(), params);

        // then
        assertThat(transactions.size(), is(2));

        assertThat(transactions.get(0).getExternalId(), is(charge1.getExternalChargeId()));
        assertThat(transactions.get(0).getTransactionType(), is("refund"));

        assertThat(transactions.get(1).getExternalId(), is(charge1.getExternalChargeId()));
        assertThat(transactions.get(1).getTransactionType(), is("charge"));
    }

    @Test
    public void shouldGetExpectedTotalCount() {

        // given
        DatabaseFixtures.TestCharge charge1 = insertNewChargeWithId(1L, now().plusHours(1));
        insertNewRefundForCharge(charge1, 2L, now().plusHours(2));
        DatabaseFixtures.TestCharge charge2 = insertNewChargeWithId(3L, now().plusHours(3));
        insertNewRefundForCharge(charge2, 4L, now().plusHours(4));
        insertNewRefundForCharge(charge2, 5L, now().plusHours(5));
        DatabaseFixtures.TestCharge charge3 = insertNewChargeWithId(6L, now().plusHours(6));
        insertNewChargeWithId(6L, now().plusHours(7));
        insertNewRefundForCharge(charge3, 5L, now().plusHours(8));

        ChargeSearchParams params = new ChargeSearchParams()
                .withPage(1L)
                .withDisplaySize(2L);

        // when
        Long count = transactionDao.getTotalFor(defaultTestAccount.getAccountId(), params);

        // then
        assertThat("total count for transactions matches", count, is(8L));

    }

    @Test
    public void searchChargesByFullReferenceOnly() throws Exception {

        // given
        DatabaseFixtures.TestCharge testCharge = insertNewChargeWithId(1L, now().plusHours(1));
        insertNewRefundForCharge(testCharge, 2L, now().plusHours(2));
        ChargeSearchParams params = new ChargeSearchParams()
                .withReferenceLike(testCharge.getReference());

        // when
        List<Transaction> transactions = transactionDao.findAllBy(defaultTestAccount.getAccountId(), params);

        // then
        assertThat(transactions.size(), is(2));

        Transaction refund = transactions.get(0);
        Transaction charge = transactions.get(1);

        assertThat(refund.getExternalId(), is(testCharge.getExternalChargeId()));
        assertThat(refund.getTransactionType(), is("refund"));
        assertThat(charge.getExternalId(), is(testCharge.getExternalChargeId()));
        assertThat(charge.getTransactionType(), is("charge"));
    }

    @Test
    public void searchChargesByPartialReferenceOnly() throws Exception {

        // given
        DatabaseFixtures.TestCharge testCharge = insertNewChargeWithId(1L, now().plusHours(1));
        insertNewRefundForCharge(testCharge, 2L, now().plusHours(2));

        String partialPaymentReference = "Council Tax";

        DatabaseFixtures.TestCharge partialReferenceCharge =
                withDatabaseTestHelper(databaseTestHelper)
                        .aTestCharge()
                        .withReference(partialPaymentReference + " whatever")
                        .withTestAccount(defaultTestAccount)
                        .withCreatedDate(now().plusHours(3))
                        .insert();

        ChargeSearchParams params = new ChargeSearchParams()
                .withReferenceLike(partialPaymentReference);

        // when
        List<Transaction> transactions = transactionDao.findAllBy(defaultTestAccount.getAccountId(), params);

        // then
        assertThat(transactions.size(), is(3));
        assertThat(transactions.get(0).getExternalId(), is(partialReferenceCharge.getExternalChargeId()));
        assertThat(transactions.get(0).getReference(), is(partialReferenceCharge.getReference()));
        assertThat(transactions.get(0).getTransactionType(), is("charge"));

        assertThat(transactions.get(1).getExternalId(), is(testCharge.getExternalChargeId()));
        assertThat(transactions.get(1).getTransactionType(), is("refund"));

        assertThat(transactions.get(2).getExternalId(), is(testCharge.getExternalChargeId()));
        assertThat(transactions.get(2).getReference(), is(testCharge.getReference()));
        assertThat(transactions.get(2).getTransactionType(), is("charge"));
    }

    @Test
    public void searchChargesByReferenceAndEmail_with_under_score() throws Exception {
        // since '_' have special meaning in like queries of postgres this was resulting in undesired results
        withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withReference("under_score_ref")
                .withEmail("under_score@mail.com")
                .insert();

        withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withReference("understand")
                .withEmail("undertaker@mail.com")
                .insert();

        ChargeSearchParams params = new ChargeSearchParams()
                .withReferenceLike("under_")
                .withEmailLike("under_");

        // when
        List<Transaction> transactions = transactionDao.findAllBy(defaultTestAccount.getAccountId(), params);

        // then
        assertThat(transactions.size(), is(1));

        Transaction transaction = transactions.get(0);
        assertThat(transaction.getReference(), is("under_score_ref"));
        assertThat(transaction.getEmail(), is("under_score@mail.com"));
    }

    @Test
    public void searchChargesByReferenceWithPercentSign() throws Exception {
        // since '%' have special meaning in like queries of postgres this was resulting in undesired results
        withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withReference("percent%ref")
                .insert();

        withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withReference("percentref")
                .insert();

        ChargeSearchParams params = new ChargeSearchParams()
                .withReferenceLike("percent%");

        // when
        List<Transaction> transactions = transactionDao.findAllBy(defaultTestAccount.getAccountId(), params);

        // then
        assertThat(transactions.size(), is(1));

        Transaction transaction = transactions.get(0);
        assertThat(transaction.getReference(), is("percent%ref"));
    }

    @Test
    public void searchTransactionByReferenceAndEmailShouldBeCaseInsensitive() throws Exception {
        // fix that the reference and email searches should be case insensitive
        withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withEmail("email-id@mail.com")
                .withReference("case-Insensitive-ref")
                .insert();

        withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withEmail("EMAIL-ID@MAIL.COM")
                .withReference("Case-inSENSITIVE-Ref")
                .insert();

        ChargeSearchParams params = new ChargeSearchParams()
                .withReferenceLike("cASe-insEnsiTIve")
                .withEmailLike("EMAIL-ID@mail.com");

        // when
        List<Transaction> transactions = transactionDao.findAllBy(defaultTestAccount.getAccountId(), params);

        // then
        assertThat(transactions.size(), is(2));

        Transaction transaction = transactions.get(0);
        assertThat(transaction.getReference(), is("Case-inSENSITIVE-Ref"));
        assertThat(transaction.getEmail(), is("EMAIL-ID@MAIL.COM"));

        transaction = transactions.get(1);
        assertThat(transaction.getReference(), is("case-Insensitive-ref"));
        assertThat(transaction.getEmail(), is("email-id@mail.com"));
    }

    @Ignore
    @Test
    public void aBasicTestAgainstSqlInjection() throws Exception {
        // given
        ChargeSearchParams params = new ChargeSearchParams()
                .withReferenceLike("reference");
        // when passed in a simple reference string
        List<Transaction> transactions = transactionDao.findAllBy(defaultTestAccount.getAccountId(), params);
        // then it fetches a single result
        assertThat(transactions.size(), is(1));

        // when passed in a non existent reference with an sql injected string
        String sqlInjectionReferenceString = "reffff%' or 1=1 or c.reference like '%1";
        params = new ChargeSearchParams()
                .withReferenceLike(sqlInjectionReferenceString);
        transactions = transactionDao.findAllBy(defaultTestAccount.getAccountId(), params);
        // then it fetches no result
        // with a typical sql injection vulnerable query doing this should fetch all results
        assertThat(transactions.size(), is(0));
    }


    @Test
    public void searchTransactionByReferenceAndLegacyStatusOnly() throws Exception {
        // given
        DatabaseFixtures.TestCharge testCharge = insertNewChargeWithId(1L, now());
        DatabaseFixtures.TestCardDetails testCardDetails = updateCardDetailsForCharge(testCharge);
        DatabaseFixtures.TestRefund testRefund = insertNewRefundForCharge(testCharge, 2L, now().plusSeconds(1));

        ChargeSearchParams params = new ChargeSearchParams()
                .withReferenceLike(testCharge.getReference())
                .withExternalChargeState(EXTERNAL_CREATED.getStatus());

        // when
        List<Transaction> transactions = transactionDao.findAllBy(defaultTestAccount.getAccountId(), params);

        // then
        assertThat(transactions.size(), is(2));
        Transaction refund = transactions.get(0);
        Transaction charge = transactions.get(1);

        assertThat(charge.getTransactionType(), is("charge"));
        assertThat(charge.getChargeId(), is(testCharge.getChargeId()));
        assertThat(charge.getAmount(), is(testCharge.getAmount()));
        assertThat(charge.getReference(), is(testCharge.getReference()));
        assertThat(charge.getDescription(), is(testCharge.getDescription()));
        assertThat(charge.getStatus(), is(testCharge.getChargeStatus().toString()));
        assertThat(charge.getCardBrand(), is(testCardDetails.getCardBrand()));

        assertDateMatch(charge.getCreatedDate().toString());

        assertThat(refund.getTransactionType(), is("refund"));
        assertThat(refund.getChargeId(), is(testCharge.getChargeId()));
        assertThat(refund.getAmount(), is(testRefund.getAmount()));
        assertThat(refund.getReference(), is(testCharge.getReference()));
        assertThat(refund.getDescription(), is(testCharge.getDescription()));
        assertThat(refund.getStatus(), is(testRefund.getStatus().toString()));
        assertThat(refund.getCardBrand(), is(testCardDetails.getCardBrand()));

        assertDateMatch(refund.getCreatedDate().toString());
    }

    @Test
    public void searchTransactionByReferenceAndStatusAndFromDateAndToDate() throws Exception {

        // given
        DatabaseFixtures.TestCharge testCharge = insertNewChargeWithId(1L, now());
        DatabaseFixtures.TestCardDetails testCardDetails = updateCardDetailsForCharge(testCharge);
        DatabaseFixtures.TestRefund testRefund = insertNewRefundForCharge(testCharge, 2L, now().plusSeconds(1));

        ChargeSearchParams params = new ChargeSearchParams()
                .withReferenceLike(testCharge.getReference())
                .withExternalChargeState(EXTERNAL_CREATED.getStatus())
                .withFromDate(ZonedDateTime.parse(FROM_DATE))
                .withToDate(ZonedDateTime.parse(TO_DATE));

        // when
        List<Transaction> transactions = transactionDao.findAllBy(defaultTestAccount.getAccountId(), params);

        // then
        assertThat(transactions.size(), is(2));

        Transaction transactionRefund = transactions.get(0);
        assertThat(transactionRefund.getTransactionType(), is("refund"));
        assertThat(transactionRefund.getChargeId(), is(testCharge.getChargeId()));
        assertThat(transactionRefund.getAmount(), is(testRefund.getAmount()));
        assertThat(transactionRefund.getReference(), is(testCharge.getReference()));
        assertThat(transactionRefund.getDescription(), is(testCharge.getDescription()));
        assertThat(transactionRefund.getStatus(), is(testRefund.getStatus().toString()));
        assertThat(transactionRefund.getCardBrand(), is(testCardDetails.getCardBrand()));
        assertDateMatch(transactionRefund.getCreatedDate().toString());

        Transaction transactionCharge = transactions.get(1);
        assertThat(transactionCharge.getTransactionType(), is("charge"));
        assertThat(transactionCharge.getChargeId(), is(testCharge.getChargeId()));
        assertThat(transactionCharge.getAmount(), is(testCharge.getAmount()));
        assertThat(transactionCharge.getReference(), is(testCharge.getReference()));
        assertThat(transactionCharge.getDescription(), is(testCharge.getDescription()));
        assertThat(transactionCharge.getStatus(), is(testCharge.getChargeStatus().toString()));
        assertThat(transactionCharge.getCardBrand(), is(testCardDetails.getCardBrand()));
        assertDateMatch(transactionCharge.getCreatedDate().toString());
    }

    @Test
    public void searchChargeByReferenceAndStatusAndFromDate() throws Exception {

        // given
        DatabaseFixtures.TestCharge testCharge = insertNewChargeWithId(1L, now());
        DatabaseFixtures.TestCardDetails testCardDetails = updateCardDetailsForCharge(testCharge);
        insertNewRefundForCharge(testCharge, 2L, now().plusSeconds(2));

        ChargeSearchParams params = new ChargeSearchParams()
                .withTransactionType("charge")
                .withReferenceLike(testCharge.getReference())
                .withExternalChargeState(EXTERNAL_CREATED.getStatus())
                .withFromDate(ZonedDateTime.parse(FROM_DATE));

        // when
        List<Transaction> transactions = transactionDao.findAllBy(defaultTestAccount.getAccountId(), params);

        // then
        assertThat(transactions.size(), is(1));

        Transaction transactionCharge = transactions.get(0);
        assertThat(transactionCharge.getTransactionType(), is("charge"));
        assertThat(transactionCharge.getChargeId(), is(testCharge.getChargeId()));
        assertThat(transactionCharge.getAmount(), is(testCharge.getAmount()));
        assertThat(transactionCharge.getReference(), is(testCharge.getReference()));
        assertThat(transactionCharge.getDescription(), is(testCharge.getDescription()));
        assertThat(transactionCharge.getStatus(), is(testCharge.getChargeStatus().toString()));
        assertThat(transactionCharge.getCardBrand(), is(testCardDetails.getCardBrand()));
        assertDateMatch(transactionCharge.getCreatedDate().toString());
    }

    @Test
    public void searchRefundByReferenceAndRefundStatusAndFromDate() throws Exception {

        // given
        DatabaseFixtures.TestCharge testCharge = insertNewChargeWithId(1L, now());
        DatabaseFixtures.TestCardDetails testCardDetails = updateCardDetailsForCharge(testCharge);
        DatabaseFixtures.TestRefund testRefund = insertNewRefundForCharge(testCharge, 2L, now().plusSeconds(2));

        ChargeSearchParams params = new ChargeSearchParams()
                .withTransactionType("refund")
                .withReferenceLike(testCharge.getReference())
                .withExternalRefundState(EXTERNAL_SUBMITTED.getStatus())
                .withFromDate(ZonedDateTime.parse(FROM_DATE));

        // when
        List<Transaction> transactions = transactionDao.findAllBy(defaultTestAccount.getAccountId(), params);

        // then
        assertThat(transactions.size(), is(1));
        Transaction refundTransaction = transactions.get(0);

        assertThat(refundTransaction.getTransactionType(), is("refund"));
        assertThat(refundTransaction.getChargeId(), is(testCharge.getChargeId()));
        assertThat(refundTransaction.getAmount(), is(testRefund.getAmount()));
        assertThat(refundTransaction.getReference(), is(testCharge.getReference()));
        assertThat(refundTransaction.getDescription(), is(testCharge.getDescription()));
        assertThat(refundTransaction.getStatus(), is(testRefund.getStatus().toString()));
        assertThat(refundTransaction.getCardBrand(), is(testCardDetails.getCardBrand()));
        assertDateMatch(refundTransaction.getCreatedDate().toString());
    }

    @Test
    public void searchBySingleChargeStatus() {

        // given
        DatabaseFixtures.TestCharge charge1 =
                withDatabaseTestHelper(databaseTestHelper)
                        .aTestCharge()
                        .withTestAccount(defaultTestAccount)
                        .withChargeStatus(ChargeStatus.CREATED)
                        .withCreatedDate(now())
                        .insert();

        DatabaseFixtures.TestCharge charge2 =
                withDatabaseTestHelper(databaseTestHelper)
                        .aTestCharge()
                        .withTestAccount(defaultTestAccount)
                        .withChargeStatus(ENTERING_CARD_DETAILS)
                        .withCreatedDate(now().plusMinutes(1))
                        .insert();


        withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeStatus(AUTHORISATION_READY)
                .withCreatedDate(now().plusMinutes(2))
                .insert();

        withDatabaseTestHelper(databaseTestHelper)
                .aTestRefund()
                .withRefundStatus(CREATED)
                .withTestCharge(charge1)
                .withCreatedDate(now().plusMinutes(3))
                .insert();

        withDatabaseTestHelper(databaseTestHelper)
                .aTestRefund()
                .withRefundStatus(REFUNDED)
                .withTestCharge(charge2)
                .withCreatedDate(now().plusMinutes(4))
                .insert();

        ChargeSearchParams params = new ChargeSearchParams()
                .withExternalChargeState(EXTERNAL_STARTED.getStatus());

        // when
        List<Transaction> transactions = transactionDao.findAllBy(defaultTestAccount.getAccountId(), params);

        // then
        assertThat(transactions.size(), is(4));
        assertThat(transactions.get(0).getTransactionType(), is("refund"));
        assertThat(transactions.get(0).getStatus(), is(RefundStatus.REFUNDED.getValue()));
        assertThat(transactions.get(1).getTransactionType(), is("refund"));
        assertThat(transactions.get(1).getStatus(), is(RefundStatus.CREATED.getValue()));
        assertThat(transactions.get(2).getTransactionType(), is("charge"));
        assertThat(transactions.get(2).getStatus(), is(AUTHORISATION_READY.getValue()));
        assertThat(transactions.get(3).getTransactionType(), is("charge"));
        assertThat(transactions.get(3).getStatus(), is(ENTERING_CARD_DETAILS.getValue()));
    }

    @Test
    public void searchByMultipleChargeStatuses() {

        // given
        DatabaseFixtures.TestCharge charge1 =
                withDatabaseTestHelper(databaseTestHelper)
                        .aTestCharge()
                        .withTestAccount(defaultTestAccount)
                        .withChargeStatus(ChargeStatus.CREATED)
                        .withCreatedDate(now())
                        .insert();

        DatabaseFixtures.TestCharge charge2 =
                withDatabaseTestHelper(databaseTestHelper)
                        .aTestCharge()
                        .withTestAccount(defaultTestAccount)
                        .withChargeStatus(ENTERING_CARD_DETAILS)
                        .withCreatedDate(now().plusMinutes(1))
                        .insert();


        withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeStatus(AUTHORISATION_READY)
                .withCreatedDate(now().plusMinutes(2))
                .insert();

        withDatabaseTestHelper(databaseTestHelper)
                .aTestRefund()
                .withRefundStatus(CREATED)
                .withTestCharge(charge1)
                .withCreatedDate(now().plusMinutes(3))
                .insert();

        withDatabaseTestHelper(databaseTestHelper)
                .aTestRefund()
                .withRefundStatus(REFUNDED)
                .withTestCharge(charge2)
                .withCreatedDate(now().plusMinutes(4))
                .insert();

        ChargeSearchParams params = new ChargeSearchParams()
                .withExternalChargeState(EXTERNAL_STARTED.getStatus())
                .withExternalChargeState(EXTERNAL_CREATED.getStatus());

        // when
        List<Transaction> transactions = transactionDao.findAllBy(defaultTestAccount.getAccountId(), params);

        // then
        assertThat(transactions.size(), is(5));
        assertThat(transactions.get(0).getTransactionType(), is("refund"));
        assertThat(transactions.get(0).getStatus(), is(RefundStatus.REFUNDED.getValue()));
        assertThat(transactions.get(1).getTransactionType(), is("refund"));
        assertThat(transactions.get(1).getStatus(), is(RefundStatus.CREATED.getValue()));
        assertThat(transactions.get(2).getTransactionType(), is("charge"));
        assertThat(transactions.get(2).getStatus(), is(AUTHORISATION_READY.getValue()));
        assertThat(transactions.get(3).getTransactionType(), is("charge"));
        assertThat(transactions.get(3).getStatus(), is(ENTERING_CARD_DETAILS.getValue()));
        assertThat(transactions.get(4).getTransactionType(), is("charge"));
        assertThat(transactions.get(4).getStatus(), is(ChargeStatus.CREATED.getValue()));
    }

    @Test
    public void searchBySingleChargeStatusAndTransactionType() {
        // given
        DatabaseFixtures.TestCharge charge1 =
                withDatabaseTestHelper(databaseTestHelper)
                        .aTestCharge()
                        .withTestAccount(defaultTestAccount)
                        .withChargeStatus(ChargeStatus.CREATED)
                        .withCreatedDate(now())
                        .insert();

        DatabaseFixtures.TestCharge charge2 =
                withDatabaseTestHelper(databaseTestHelper)
                        .aTestCharge()
                        .withTestAccount(defaultTestAccount)
                        .withChargeStatus(ENTERING_CARD_DETAILS)
                        .withCreatedDate(now().plusMinutes(1))
                        .insert();


        withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeStatus(AUTHORISATION_READY)
                .withCreatedDate(now().plusMinutes(2))
                .insert();

        withDatabaseTestHelper(databaseTestHelper)
                .aTestRefund()
                .withRefundStatus(CREATED)
                .withTestCharge(charge1)
                .withCreatedDate(now().plusMinutes(3))
                .insert();

        withDatabaseTestHelper(databaseTestHelper)
                .aTestRefund()
                .withRefundStatus(REFUNDED)
                .withTestCharge(charge2)
                .withCreatedDate(now().plusMinutes(4))
                .insert();

        ChargeSearchParams params = new ChargeSearchParams()
                .withTransactionType("charge")
                .withExternalChargeState(EXTERNAL_STARTED.getStatus());

        // when
        List<Transaction> transactions = transactionDao.findAllBy(defaultTestAccount.getAccountId(), params);

        // then
        assertThat(transactions.size(), is(2));
        assertThat(transactions.get(0).getTransactionType(), is("charge"));
        assertThat(transactions.get(0).getStatus(), is(AUTHORISATION_READY.getValue()));
        assertThat(transactions.get(1).getTransactionType(), is("charge"));
        assertThat(transactions.get(1).getStatus(), is(ENTERING_CARD_DETAILS.getValue()));
    }

    @Test
    public void searchBySingleRefundStatus() {

        // given
        DatabaseFixtures.TestCharge charge1 =
                withDatabaseTestHelper(databaseTestHelper)
                        .aTestCharge()
                        .withTestAccount(defaultTestAccount)
                        .withChargeStatus(ChargeStatus.CREATED)
                        .withCreatedDate(now())
                        .insert();

        DatabaseFixtures.TestCharge charge2 =
                withDatabaseTestHelper(databaseTestHelper)
                        .aTestCharge()
                        .withTestAccount(defaultTestAccount)
                        .withChargeStatus(ENTERING_CARD_DETAILS)
                        .withCreatedDate(now().plusMinutes(1))
                        .insert();


        withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeStatus(AUTHORISATION_READY)
                .withCreatedDate(now().plusMinutes(2))
                .insert();

        withDatabaseTestHelper(databaseTestHelper)
                .aTestRefund()
                .withRefundStatus(CREATED)
                .withTestCharge(charge1)
                .withCreatedDate(now().plusMinutes(3))
                .insert();

        withDatabaseTestHelper(databaseTestHelper)
                .aTestRefund()
                .withRefundStatus(REFUNDED)
                .withTestCharge(charge2)
                .withCreatedDate(now().plusMinutes(4))
                .insert();

        ChargeSearchParams params = new ChargeSearchParams()
                .withExternalRefundState(EXTERNAL_SUCCESS.getStatus());

        // when
        List<Transaction> transactions = transactionDao.findAllBy(defaultTestAccount.getAccountId(), params);

        // then
        assertThat(transactions.size(), is(4));
        assertThat(transactions.get(0).getTransactionType(), is("refund"));
        assertThat(transactions.get(0).getStatus(), is(RefundStatus.REFUNDED.getValue()));
        assertThat(transactions.get(1).getTransactionType(), is("charge"));
        assertThat(transactions.get(1).getStatus(), is(AUTHORISATION_READY.getValue()));
        assertThat(transactions.get(2).getTransactionType(), is("charge"));
        assertThat(transactions.get(2).getStatus(), is(ENTERING_CARD_DETAILS.getValue()));
        assertThat(transactions.get(3).getTransactionType(), is("charge"));
        assertThat(transactions.get(3).getStatus(), is(ChargeStatus.CREATED.getValue()));
    }

    @Test
    public void searchByMultipleRefundStatus() {

        // given
        DatabaseFixtures.TestCharge charge1 =
                withDatabaseTestHelper(databaseTestHelper)
                        .aTestCharge()
                        .withTestAccount(defaultTestAccount)
                        .withChargeStatus(ChargeStatus.CREATED)
                        .withCreatedDate(now())
                        .insert();

        DatabaseFixtures.TestCharge charge2 =
                withDatabaseTestHelper(databaseTestHelper)
                        .aTestCharge()
                        .withTestAccount(defaultTestAccount)
                        .withChargeStatus(ENTERING_CARD_DETAILS)
                        .withCreatedDate(now().plusMinutes(1))
                        .insert();


        withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeStatus(AUTHORISATION_READY)
                .withCreatedDate(now().plusMinutes(2))
                .insert();

        withDatabaseTestHelper(databaseTestHelper)
                .aTestRefund()
                .withRefundStatus(CREATED)
                .withTestCharge(charge1)
                .withCreatedDate(now().plusMinutes(3))
                .insert();

        withDatabaseTestHelper(databaseTestHelper)
                .aTestRefund()
                .withRefundStatus(REFUNDED)
                .withTestCharge(charge2)
                .withCreatedDate(now().plusMinutes(4))
                .insert();

        ChargeSearchParams params = new ChargeSearchParams()
                .withExternalRefundState(EXTERNAL_SUBMITTED.getStatus())
                .withExternalRefundState(EXTERNAL_SUCCESS.getStatus());

        // when
        List<Transaction> transactions = transactionDao.findAllBy(defaultTestAccount.getAccountId(), params);

        // then
        assertThat(transactions.size(), is(5));
        assertThat(transactions.get(0).getTransactionType(), is("refund"));
        assertThat(transactions.get(0).getStatus(), is(RefundStatus.REFUNDED.getValue()));
        assertThat(transactions.get(1).getTransactionType(), is("refund"));
        assertThat(transactions.get(1).getStatus(), is(RefundStatus.CREATED.getValue()));
        assertThat(transactions.get(2).getTransactionType(), is("charge"));
        assertThat(transactions.get(2).getStatus(), is(AUTHORISATION_READY.getValue()));
        assertThat(transactions.get(3).getTransactionType(), is("charge"));
        assertThat(transactions.get(3).getStatus(), is(ENTERING_CARD_DETAILS.getValue()));
        assertThat(transactions.get(4).getTransactionType(), is("charge"));
        assertThat(transactions.get(4).getStatus(), is(ChargeStatus.CREATED.getValue()));
    }

    @Test
    public void searchBySingleRefundStatusAndTransactionType() {

        // given
        DatabaseFixtures.TestCharge charge1 =
                withDatabaseTestHelper(databaseTestHelper)
                        .aTestCharge()
                        .withTestAccount(defaultTestAccount)
                        .withChargeStatus(ChargeStatus.CREATED)
                        .withCreatedDate(now())
                        .insert();

        DatabaseFixtures.TestCharge charge2 =
                withDatabaseTestHelper(databaseTestHelper)
                        .aTestCharge()
                        .withTestAccount(defaultTestAccount)
                        .withChargeStatus(ENTERING_CARD_DETAILS)
                        .withCreatedDate(now().plusMinutes(1))
                        .insert();


        withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeStatus(AUTHORISATION_READY)
                .withCreatedDate(now().plusMinutes(2))
                .insert();

        withDatabaseTestHelper(databaseTestHelper)
                .aTestRefund()
                .withRefundStatus(CREATED)
                .withTestCharge(charge1)
                .withCreatedDate(now().plusMinutes(3))
                .insert();

        withDatabaseTestHelper(databaseTestHelper)
                .aTestRefund()
                .withRefundStatus(REFUNDED)
                .withTestCharge(charge2)
                .withCreatedDate(now().plusMinutes(4))
                .insert();
        ChargeSearchParams params = new ChargeSearchParams()
                .withTransactionType("refund")
                .withExternalRefundState(EXTERNAL_SUCCESS.getStatus());

        // when
        List<Transaction> transactions = transactionDao.findAllBy(defaultTestAccount.getAccountId(), params);

        // then
        assertThat(transactions.size(), is(1));
        assertThat(transactions.get(0).getTransactionType(), is("refund"));
        assertThat(transactions.get(0).getStatus(), is(RefundStatus.REFUNDED.getValue()));
    }

    @Test
    public void searchChargeByReferenceAndStatusAndEmailAndCardBrandAndFromDateAndToDate() throws Exception {
        // given
        DatabaseFixtures.TestCharge testCharge = insertNewChargeWithId(1L, now());
        DatabaseFixtures.TestCardDetails testCardDetails = updateCardDetailsForCharge(testCharge);
        insertNewRefundForCharge(testCharge, 2L, now().plusSeconds(2));

        ChargeSearchParams params = new ChargeSearchParams()
                .withTransactionType("charge")
                .withReferenceLike(testCharge.getReference())
                .withExternalChargeState(EXTERNAL_CREATED.getStatus())
                .withCardBrand(testCardDetails.getCardBrand())
                .withEmailLike(testCharge.getEmail())
                .withFromDate(ZonedDateTime.parse(FROM_DATE))
                .withToDate(ZonedDateTime.parse(TO_DATE));

        // when
        List<Transaction> transactions = transactionDao.findAllBy(defaultTestAccount.getAccountId(), params);

        // then
        assertThat(transactions.size(), is(1));
        Transaction charge = transactions.get(0);

        assertThat(charge.getTransactionType(), is("charge"));
        assertThat(charge.getChargeId(), is(testCharge.getChargeId()));
        assertThat(charge.getAmount(), is(testCharge.getAmount()));
        assertThat(charge.getReference(), is(testCharge.getReference()));
        assertThat(charge.getDescription(), is(testCharge.getDescription()));
        assertThat(charge.getStatus(), is(testCharge.getChargeStatus().toString()));
        assertThat(charge.getCardBrand(), is(testCardDetails.getCardBrand()));
        assertDateMatch(charge.getCreatedDate().toString());
    }

    @Test
    public void searchChargeByReferenceAndStatusAndToDate() throws Exception {

        // given
        DatabaseFixtures.TestCharge testCharge = insertNewChargeWithId(1L, now());
        DatabaseFixtures.TestCardDetails testCardDetails = updateCardDetailsForCharge(testCharge);
        insertNewRefundForCharge(testCharge, 2L, now().plusSeconds(2));

        ChargeSearchParams params = new ChargeSearchParams()
                .withTransactionType("charge")
                .withReferenceLike(testCharge.getReference())
                .withExternalChargeState(EXTERNAL_CREATED.getStatus())
                .withToDate(ZonedDateTime.parse(TO_DATE));

        // when
        List<Transaction> transactions = transactionDao.findAllBy(defaultTestAccount.getAccountId(), params);

        // then
        assertThat(transactions.size(), is(1));
        Transaction charge = transactions.get(0);

        assertThat(charge.getTransactionType(), is("charge"));
        assertThat(charge.getChargeId(), is(testCharge.getChargeId()));
        assertThat(charge.getAmount(), is(testCharge.getAmount()));
        assertThat(charge.getReference(), is(testCharge.getReference()));
        assertThat(charge.getDescription(), is(DEFAULT_TEST_CHARGE_DESCRIPTION));
        assertThat(charge.getStatus(), is(testCharge.getChargeStatus().toString()));
        assertThat(charge.getCardBrand(), is(testCardDetails.getCardBrand()));

        assertDateMatch(charge.getCreatedDate().toString());
    }

    @Test
    public void searchTransactionByReferenceAndStatusAndFromDate_ShouldReturnZeroIfDateIsNotInRange() throws Exception {

        // given
        DatabaseFixtures.TestCharge testCharge = insertNewChargeWithId(1L, now().plusHours(1));
        insertNewRefundForCharge(testCharge, 2L, now().plusHours(2));

        ChargeSearchParams params = new ChargeSearchParams()
                .withReferenceLike(testCharge.getReference())
                .withExternalChargeState(EXTERNAL_CREATED.getStatus())
                .withFromDate(ZonedDateTime.parse(TO_DATE));

        // when
        List<Transaction> transactions = transactionDao.findAllBy(defaultTestAccount.getAccountId(), params);

        // then
        assertThat(transactions.size(), is(0));
    }

    @Test
    public void searchTransactionByReferenceAndStatusAndToDate_ShouldReturnZeroIfToDateIsNotInRange() throws Exception {

        // given
        DatabaseFixtures.TestCharge testCharge = insertNewChargeWithId(1L, now().plusHours(1));
        insertNewRefundForCharge(testCharge, 2L, now().plusHours(2));

        ChargeSearchParams params = new ChargeSearchParams()
                .withReferenceLike(testCharge.getReference())
                .withExternalChargeState(EXTERNAL_CREATED.getStatus())
                .withToDate(ZonedDateTime.parse(FROM_DATE));

        // when
        List<Transaction> transactions = transactionDao.findAllBy(defaultTestAccount.getAccountId(), params);

        // then
        assertThat(transactions.size(), is(0));
    }

    private void assertDateMatch(String createdDateString) {
        assertDateMatch(DateTimeUtils.toUTCZonedDateTime(createdDateString).get());
    }

    private void assertDateMatch(ZonedDateTime createdDateTime) {
        assertThat(createdDateTime, within(1, ChronoUnit.MINUTES, ZonedDateTime.now()));
    }

    private DatabaseFixtures.TestCharge insertNewChargeWithId(long amount, ZonedDateTime creationDate) {
        return
                withDatabaseTestHelper(databaseTestHelper)
                        .aTestCharge()
                        .withDescription(DEFAULT_TEST_CHARGE_DESCRIPTION)
                        .withReference(DEFAULT_TEST_CHARGE_REFERENCE)
                        .withAmount(amount)
                        .withTestAccount(defaultTestAccount)
                        .withCreatedDate(creationDate)
                        .insert();
    }

    private DatabaseFixtures.TestCardDetails updateCardDetailsForCharge(DatabaseFixtures.TestCharge charge) {
        return new DatabaseFixtures(databaseTestHelper)
                .validTestCardDetails()
                .withChargeId(charge.chargeId)
                .update();
    }

    private DatabaseFixtures.TestRefund insertNewRefundForCharge(DatabaseFixtures.TestCharge charge, long amount, ZonedDateTime creationDate) {
        return
                withDatabaseTestHelper(databaseTestHelper)
                        .aTestRefund()
                        .withAmount(amount)
                        .withTestCharge(charge)
                        .withCreatedDate(creationDate)
                        .insert();
    }
}
