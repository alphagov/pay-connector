package uk.gov.pay.connector.it.dao;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.dao.ChargeSearchParams;
import uk.gov.pay.connector.dao.TransactionDao;
import uk.gov.pay.connector.model.domain.Transaction;
import uk.gov.pay.connector.model.api.ExternalRefundStatus;
import uk.gov.pay.connector.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.RefundStatus;
import uk.gov.pay.connector.util.DateTimeUtils;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static java.time.ZonedDateTime.now;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.exparity.hamcrest.date.ZonedDateTimeMatchers.within;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.fail;
import static uk.gov.pay.connector.model.api.ExternalChargeState.EXTERNAL_CREATED;
import static uk.gov.pay.connector.model.api.ExternalChargeState.EXTERNAL_STARTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class TransactionDaoITest extends DaoITestBase {

    private static final String FROM_DATE = "2016-01-01T01:00:00Z";
    private static final String TO_DATE = "2026-01-08T01:00:00Z";
    private static final String DEFAULT_TEST_CHARGE_DESCRIPTION = "Charge description";
    private static final String DEFAULT_TEST_CHARGE_REFERENCE = "Council tax thing";
    private static final long DEFAULT_TEST_CHARGE_AMOUNT = 1000L;
    private static final long DEFAULT_TEST_REFUND_AMOUNT = 500L;

    private TransactionDao transactionDao;
    private DatabaseFixtures.TestAccount defaultTestAccount;
    private DatabaseFixtures.TestRefund defaultTestRefund;
    private DatabaseFixtures.TestCharge defaultTestCharge;
    private DatabaseFixtures.TestCardDetails defaultTestCardDetails;

    @Before
    public void setupAccountWithOneChargeAndRefundForTheCharge() {

        transactionDao = env.getInstance(TransactionDao.class);

        defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .insert();

        defaultTestCardDetails = new DatabaseFixtures(databaseTestHelper).validTestCardDetails();

        defaultTestCharge = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withDescription(DEFAULT_TEST_CHARGE_DESCRIPTION)
                .withReference(DEFAULT_TEST_CHARGE_REFERENCE)
                .withAmount(DEFAULT_TEST_CHARGE_AMOUNT)
                .withTestAccount(defaultTestAccount)
                .insert();

        defaultTestCardDetails
                .withChargeId(defaultTestCharge.chargeId)
                .update();

        defaultTestRefund = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestRefund()
                .withAmount(DEFAULT_TEST_REFUND_AMOUNT)
                .withTestCharge(defaultTestCharge)
                .withCreatedDate(now().plusMinutes(10))
                .insert();
    }

    @Test
    public void searchChargesByGatewayAccountIdOnly() throws Exception {

        // given
        ChargeSearchParams params = new ChargeSearchParams()
                .withGatewayAccountId(defaultTestAccount.getAccountId());

        // when
        List<Transaction> transactions = transactionDao.findAllBy(params);

        // then
        assertThat(transactions.size(), is(2));

        Transaction transactionRefund = transactions.get(0);
        assertThat(transactionRefund.getTransactionType(), is("refund"));
        assertThat(transactionRefund.getAmount(), is(DEFAULT_TEST_REFUND_AMOUNT));
        assertThat(transactionRefund.getReference(), is(DEFAULT_TEST_CHARGE_REFERENCE));
        assertThat(transactionRefund.getDescription(), is(DEFAULT_TEST_CHARGE_DESCRIPTION));
        assertThat(transactionRefund.getStatus(), is(defaultTestRefund.getStatus().toString()));
        assertThat(transactionRefund.getCardBrand(), is(defaultTestCardDetails.getCardBrand()));
        assertDateMatch(transactionRefund.getCreatedDate().toString());

        Transaction transactionCharge = transactions.get(1);
        assertThat(transactionCharge.getTransactionType(), is("charge"));
        assertThat(transactionCharge.getAmount(), is(DEFAULT_TEST_CHARGE_AMOUNT));
        assertThat(transactionCharge.getReference(), is(DEFAULT_TEST_CHARGE_REFERENCE));
        assertThat(transactionCharge.getDescription(), is(DEFAULT_TEST_CHARGE_DESCRIPTION));
        assertThat(transactionCharge.getStatus(), is(defaultTestCharge.getChargeStatus().toString()));
        assertThat(transactionCharge.getCardBrand(), is(defaultTestCardDetails.getCardBrand()));
        assertDateMatch(transactionCharge.getCreatedDate().toString());
    }

    @Test
    public void searchChargesByFullEmailMatch() throws Exception {

        // given
        ChargeSearchParams params = new ChargeSearchParams()
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withEmailLike(defaultTestCharge.getEmail());

        // when
        List<Transaction> transactions = transactionDao.findAllBy(params);

        // then
        assertThat(transactions.size(), is(2));

        assertThat(transactions.get(0).getAmount(), is(defaultTestRefund.getAmount()));
        assertThat(transactions.get(0).getTransactionType(), is("refund"));
        assertThat(transactions.get(1).getAmount(), is(defaultTestCharge.getAmount()));
        assertThat(transactions.get(1).getTransactionType(), is("charge"));
    }

    @Test
    public void searchChargesByPartialEmailMatch() throws Exception {
        // given
        ChargeSearchParams params = new ChargeSearchParams()
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withEmailLike("alice");

        // when
        List<Transaction> transactions = transactionDao.findAllBy(params);

        // then
        assertThat(transactions.size(), is(2));

        assertThat(transactions.get(0).getAmount(), is(defaultTestRefund.getAmount()));
        assertThat(transactions.get(0).getTransactionType(), is("refund"));
        assertThat(transactions.get(1).getAmount(), is(defaultTestCharge.getAmount()));
        assertThat(transactions.get(1).getTransactionType(), is("charge"));
    }

    @Test
    public void searchChargesByCardBrandOnly() throws Exception {
        // given
        ChargeSearchParams params = new ChargeSearchParams()
                .withCardBrand(defaultTestCardDetails.getCardBrand());

        // when
        List<Transaction> transactions = transactionDao.findAllBy(params);

        // then
        assertThat(transactions.size(), is(2));

        assertThat(transactions.get(0).getAmount(), is(defaultTestRefund.getAmount()));
        assertThat(transactions.get(0).getTransactionType(), is("refund"));
        assertThat(transactions.get(1).getAmount(), is(defaultTestCharge.getAmount()));
        assertThat(transactions.get(1).getTransactionType(), is("charge"));
    }


    private DatabaseFixtures.TestCharge insertNewChargeWithId(long amount, ZonedDateTime creationDate) {
        return DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withAmount(amount)
                .withCreatedDate(creationDate)
                .withTestAccount(defaultTestAccount)
                .insert();
    }

    private DatabaseFixtures.TestRefund insertNewRefundForCharge(DatabaseFixtures.TestCharge charge, long amount, ZonedDateTime creationDate) {
        return DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestRefund()
                .withAmount(amount)
                .withTestCharge(charge)
                .withCreatedDate(creationDate)
                .insert();
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

        ChargeSearchParams params = new ChargeSearchParams()
                .withGatewayAccountId(defaultTestAccount.getAccountId());

        // when
        List<Transaction> transactions = transactionDao.findAllBy(params);

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
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withPage(1L)
                .withDisplaySize(3L);

        List<Transaction> transactions = transactionDao.findAllBy(params);
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

        transactions = transactionDao.findAllBy(params);

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

        transactions = transactionDao.findAllBy(params);

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
        DatabaseFixtures.TestCharge charge4 = insertNewChargeWithId(6L, now().plusHours(7));
        insertNewRefundForCharge(charge3, 5L, now().plusHours(8));

        ChargeSearchParams params = new ChargeSearchParams()
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withExternalChargeState(EXTERNAL_CREATED.getStatus())
                .withDisplaySize(2L);

        // when
        //Long count = transactionDao.getTotalFor(params);

        // then gets the count(*) irrespective of the max results (display_size)
        // assertThat("total count for transactions mismatch", count, is(5L));

        fail("WIP getTotalFor method");
    }

    @Test
    public void searchChargesByFullReferenceOnly() throws Exception {

        // given
        ChargeSearchParams params = new ChargeSearchParams()
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withReferenceLike(defaultTestCharge.getReference());

        // when
        List<Transaction> transactions = transactionDao.findAllBy(params);

        // then
        assertThat(transactions.size(), is(2));

        Transaction refund = transactions.get(0);
        Transaction charge = transactions.get(1);

        assertThat(refund.getTransactionType(), is("refund"));
        assertThat(charge.getTransactionType(), is("charge"));
    }

    @Test
    public void searchChargesByPartialReferenceOnly() throws Exception {

        // given
        String partialPaymentReference = "Council Tax";

        DatabaseFixtures.TestCharge partialReferenceCharge = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withReference(partialPaymentReference + " whatever")
                .withTestAccount(defaultTestAccount)
                .withCreatedDate(now().plusHours(3))
                .insert();

        ChargeSearchParams params = new ChargeSearchParams()
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withReferenceLike(partialPaymentReference);

        // when
        List<Transaction> transactions = transactionDao.findAllBy(params);

        // then
        assertThat(transactions.size(), is(3));
        assertThat(transactions.get(0).getExternalId(), is(partialReferenceCharge.getExternalChargeId()));
        assertThat(transactions.get(0).getReference(), is(partialReferenceCharge.getReference()));
        assertThat(transactions.get(0).getTransactionType(), is("charge"));

        assertThat(transactions.get(1).getExternalId(), is(defaultTestCharge.getExternalChargeId()));
        assertThat(transactions.get(1).getTransactionType(), is("refund"));

        assertThat(transactions.get(2).getExternalId(), is(defaultTestCharge.getExternalChargeId()));
        assertThat(transactions.get(2).getReference(), is(defaultTestCharge.getReference()));
        assertThat(transactions.get(2).getTransactionType(), is("charge"));
    }

    @Test
    public void searchChargesByReferenceAndEmail_with_under_score() throws Exception {
        // since '_' have special meaning in like queries of postgres this was resulting in undesired results
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withReference("under_score_ref")
                .withEmail("under_score@mail.com")
                .insert();

        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withReference("understand")
                .withEmail("undertaker@mail.com")
                .insert();

        ChargeSearchParams params = new ChargeSearchParams()
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withReferenceLike("under_")
                .withEmailLike("under_");

        // when
        List<Transaction> transactions = transactionDao.findAllBy(params);

        // then
        assertThat(transactions.size(), is(1));

        Transaction transaction = transactions.get(0);
        assertThat(transaction.getReference(), is("under_score_ref"));
        assertThat(transaction.getEmail(), is("under_score@mail.com"));
    }

    @Test
    public void searchChargesByReferenceWithPercentSign() throws Exception {
        // since '%' have special meaning in like queries of postgres this was resulting in undesired results
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withReference("percent%ref")
                .insert();

        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withReference("percentref")
                .insert();

        ChargeSearchParams params = new ChargeSearchParams()
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withReferenceLike("percent%");

        // when
        List<Transaction> transactions = transactionDao.findAllBy(params);

        // then
        assertThat(transactions.size(), is(1));

        Transaction transaction = transactions.get(0);
        assertThat(transaction.getReference(), is("percent%ref"));
    }

    @Test
    public void searchChargesByReferenceAndEmailShouldBeCaseInsensitive() throws Exception {
        // fix that the reference and email searches should be case insensitive
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withEmail("email-id@mail.com")
                .withReference("case-Insensitive-ref")
                .insert();

        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withEmail("EMAIL-ID@MAIL.COM")
                .withReference("Case-inSENSITIVE-Ref")
                .insert();

        ChargeSearchParams params = new ChargeSearchParams()
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withReferenceLike("cASe-insEnsiTIve")
                .withEmailLike("EMAIL-ID@mail.com");

        // when
        List<Transaction> transactions = transactionDao.findAllBy(params);

        // then
        assertThat(transactions.size(), is(2));

        Transaction transaction = transactions.get(0);
        assertThat(transaction.getReference(), is("Case-inSENSITIVE-Ref"));
        assertThat(transaction.getEmail(), is("EMAIL-ID@MAIL.COM"));

        transaction = transactions.get(1);
        assertThat(transaction.getReference(), is("case-Insensitive-ref"));
        assertThat(transaction.getEmail(), is("email-id@mail.com"));
    }

    @Test
    public void aBasicTestAgainstSqlInjection() throws Exception {
        // given
        ChargeSearchParams params = new ChargeSearchParams()
                .withReferenceLike("reference");
        // when passed in a simple reference string
        List<Transaction> transactions = transactionDao.findAllBy(params);
        // then it fetches a single result
        assertThat(transactions.size(), is(1));

        // when passed in a non existent reference with an sql injected string
        String sqlInjectionReferenceString = "reffff%' or 1=1 or c.reference like '%1";
        params = new ChargeSearchParams()
                .withReferenceLike(sqlInjectionReferenceString);
        transactions = transactionDao.findAllBy(params);
        // then it fetches no result
        // with a typical sql injection vulnerable query doing this should fetch all results
        assertThat(transactions.size(), is(0));
    }


    @Test
    public void searchChargeByReferenceAndLegacyStatusOnly() throws Exception {
        // given
        ChargeSearchParams params = new ChargeSearchParams()
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withReferenceLike(defaultTestCharge.getReference())
                .withExternalChargeState(EXTERNAL_CREATED.getStatus());

        // when
        List<Transaction> transactions = transactionDao.findAllBy(params);

        // then
        assertThat(transactions.size(), is(1));
        Transaction charge = transactions.get(0);

        assertThat(charge.getAmount(), is(defaultTestCharge.getAmount()));
        assertThat(charge.getReference(), is(defaultTestCharge.getReference()));
        assertThat(charge.getDescription(), is(DEFAULT_TEST_CHARGE_DESCRIPTION));
        assertThat(charge.getStatus(), is(defaultTestCharge.getChargeStatus().toString()));
        assertThat(charge.getCardBrand(), is(defaultTestCardDetails.getCardBrand()));
        assertDateMatch(charge.getCreatedDate().toString());
    }

    @Test
    public void searchChargeByReferenceAndStatusAndFromDateAndToDate() throws Exception {

        // given
        ChargeSearchParams params = new ChargeSearchParams()
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withReferenceLike(defaultTestCharge.getReference())
                .withExternalChargeState(EXTERNAL_CREATED.getStatus())
                .withFromDate(ZonedDateTime.parse(FROM_DATE))
                .withToDate(ZonedDateTime.parse(TO_DATE));

        // when
        List<Transaction> transactions = transactionDao.findAllBy(params);

        // then
        assertThat(transactions.size(), is(2));

        Transaction transactionRefund = transactions.get(0);
        assertThat(transactionRefund.getAmount(), is(defaultTestRefund.getAmount()));
        assertThat(transactionRefund.getReference(), is(defaultTestCharge.getReference()));
        assertThat(transactionRefund.getDescription(), is(DEFAULT_TEST_CHARGE_DESCRIPTION));
        assertThat(transactionRefund.getStatus(), is(defaultTestRefund.getStatus()));
        assertThat(transactionRefund.getCardBrand(), is(defaultTestCardDetails.getCardBrand()));
        assertDateMatch(transactionRefund.getCreatedDate().toString());

        Transaction transactionCharge = transactions.get(0);
        assertThat(transactionCharge.getAmount(), is(defaultTestCharge.getAmount()));
        assertThat(transactionCharge.getReference(), is(defaultTestCharge.getReference()));
        assertThat(transactionCharge.getDescription(), is(DEFAULT_TEST_CHARGE_DESCRIPTION));
        assertThat(transactionCharge.getStatus(), is(defaultTestCharge.getChargeStatus().toString()));
        assertThat(transactionCharge.getCardBrand(), is(defaultTestCardDetails.getCardBrand()));
        assertDateMatch(transactionCharge.getCreatedDate().toString());
    }

    @Test
    public void searchChargeByReferenceAndStatusAndFromDate() throws Exception {

        // given
        ChargeSearchParams params = new ChargeSearchParams()
                .withTransactionType("charge")
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withReferenceLike(defaultTestCharge.getReference())
                .withExternalChargeState(EXTERNAL_CREATED.getStatus())
                .withFromDate(ZonedDateTime.parse(FROM_DATE));

        // when
        List<Transaction> transactions = transactionDao.findAllBy(params);

        // then
        assertThat(transactions.size(), is(1));
        Transaction chargeTransaction = transactions.get(0);

        assertThat(chargeTransaction.getAmount(), is(defaultTestCharge.getAmount()));
        assertThat(chargeTransaction.getReference(), is(defaultTestCharge.getReference()));
        assertThat(chargeTransaction.getDescription(), is(DEFAULT_TEST_CHARGE_DESCRIPTION));
        assertThat(chargeTransaction.getStatus(), is(defaultTestCharge.getChargeStatus().toString()));
        assertThat(chargeTransaction.getCardBrand(), is(defaultTestCardDetails.getCardBrand()));

        assertDateMatch(chargeTransaction.getCreatedDate().toString());
    }

    @Test
    public void searchRefundByReferenceAndRefundStatusAndFromDate() throws Exception {

        // given
        ChargeSearchParams params = new ChargeSearchParams()
                .withTransactionType("refund")
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withReferenceLike(defaultTestCharge.getReference())
                .withExternalRefundState(ExternalRefundStatus.EXTERNAL_SUBMITTED.getStatus())
                .withFromDate(ZonedDateTime.parse(FROM_DATE));

        // when
        List<Transaction> transactions = transactionDao.findAllBy(params);

        // then
        assertThat(transactions.size(), is(1));
        Transaction chargeTransaction = transactions.get(0);

        assertThat(chargeTransaction.getAmount(), is(defaultTestRefund.getAmount()));
        assertThat(chargeTransaction.getReference(), is(defaultTestCharge.getReference()));
        assertThat(chargeTransaction.getDescription(), is(DEFAULT_TEST_CHARGE_DESCRIPTION));
        assertThat(chargeTransaction.getStatus(), is(defaultTestRefund.getStatus()));
        assertThat(chargeTransaction.getCardBrand(), is(defaultTestCardDetails.getCardBrand()));

        assertDateMatch(chargeTransaction.getCreatedDate().toString());
    }

    @Test
    public void searchChargesBySingleStatus() {
        // given
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeStatus(ENTERING_CARD_DETAILS)
                .insert();

        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeStatus(AUTHORISATION_READY)
                .insert();

        ChargeSearchParams params = new ChargeSearchParams()
                .withTransactionType("charge")
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withExternalChargeState(EXTERNAL_STARTED.getStatus());

        // when
        List<Transaction> transactions = transactionDao.findAllBy(params);

        // then
        assertThat(transactions.size(), is(2));
        assertThat(transactions.get(0).getTransactionType(), is("charge"));
        assertThat(transactions.get(0).getStatus(), is(AUTHORISATION_READY.getValue()));
        assertThat(transactions.get(1).getTransactionType(), is("charge"));
        assertThat(transactions.get(1).getStatus(), is(ENTERING_CARD_DETAILS.getValue()));
    }

    @Test
    public void searchChargesByMultipleStatuses() {
        // given
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeStatus(ENTERING_CARD_DETAILS)
                .insert();

        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeStatus(AUTHORISATION_READY)
                .insert();

        ChargeSearchParams params = new ChargeSearchParams()
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withTransactionType("charge")
                .withExternalChargeState(EXTERNAL_STARTED.getStatus())
                .withExternalChargeState(EXTERNAL_CREATED.getStatus());

        // when
        List<Transaction> transactions = transactionDao.findAllBy(params);

        // then
        assertThat(transactions.size(), is(2));
        assertThat(transactions.get(0).getTransactionType(), is("charge"));
        assertThat(transactions.get(0).getStatus(), is(AUTHORISATION_READY.getValue()));
        assertThat(transactions.get(1).getTransactionType(), is("charge"));
        assertThat(transactions.get(1).getStatus(), is(ENTERING_CARD_DETAILS.getValue()));
    }

    @Test
    public void searchRefundsByMultipleStatuses() {

        // given
        DatabaseFixtures.TestCharge otherCharge = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeStatus(CAPTURED)
                .insert();

        DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper)
                .aTestRefund()
                .withTestCharge(otherCharge)
                .withRefundStatus(RefundStatus.REFUND_ERROR)
                .insert();

        ChargeSearchParams params = new ChargeSearchParams()
                .withTransactionType("refund")
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withReferenceLike(defaultTestCharge.getReference())
                .withExternalChargeState(EXTERNAL_STARTED.getStatus())
                .withFromDate(ZonedDateTime.parse(FROM_DATE));

        // when
        List<Transaction> transactions = transactionDao.findAllBy(params);

        // then
        assertThat(transactions.size(), is(2));
        assertThat(transactions.get(0).getStatus(), is(AUTHORISATION_READY.getValue()));
        assertThat(transactions.get(1).getStatus(), is(ENTERING_CARD_DETAILS.getValue()));
    }

    @Test
    public void searchChargeByReferenceAndStatusAndEmailAndCardBrandAndFromDateAndToDate() throws Exception {
        // given
        ChargeSearchParams params = new ChargeSearchParams()
                .withTransactionType("charge")
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withReferenceLike(defaultTestCharge.getReference())
                .withExternalChargeState(EXTERNAL_CREATED.getStatus())
                .withCardBrand(defaultTestCardDetails.getCardBrand())
                .withEmailLike(defaultTestCharge.getEmail())
                .withFromDate(ZonedDateTime.parse(FROM_DATE))
                .withToDate(ZonedDateTime.parse(TO_DATE));

        // when
        List<Transaction> transactions = transactionDao.findAllBy(params);

        // then
        assertThat(transactions.size(), is(1));
        Transaction charge = transactions.get(0);

        assertThat(charge.getAmount(), is(defaultTestCharge.getAmount()));
        assertThat(charge.getReference(), is(defaultTestCharge.getReference()));
        assertThat(charge.getDescription(), is(DEFAULT_TEST_CHARGE_DESCRIPTION));
        assertThat(charge.getStatus(), is(defaultTestCharge.getChargeStatus().toString()));
        assertThat(charge.getCardBrand(), is(defaultTestCardDetails.getCardBrand()));
        assertDateMatch(charge.getCreatedDate().toString());
    }

    @Test
    public void searchChargeByReferenceAndStatusAndToDate() throws Exception {

        // given
        ChargeSearchParams params = new ChargeSearchParams()
                .withTransactionType("charge")
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withReferenceLike(defaultTestCharge.getReference())
                .withExternalChargeState(EXTERNAL_CREATED.getStatus())
                .withToDate(ZonedDateTime.parse(TO_DATE));

        // when
        List<Transaction> transactions = transactionDao.findAllBy(params);

        // then
        assertThat(transactions.size(), is(1));
        Transaction charge = transactions.get(0);

        assertThat(charge.getAmount(), is(defaultTestCharge.getAmount()));
        assertThat(charge.getReference(), is(defaultTestCharge.getReference()));
        assertThat(charge.getDescription(), is(DEFAULT_TEST_CHARGE_DESCRIPTION));
        assertThat(charge.getStatus(), is(defaultTestCharge.getChargeStatus().toString()));
        assertThat(charge.getCardBrand(), is(defaultTestCardDetails.getCardBrand()));
        assertDateMatch(charge.getCreatedDate().toString());
    }

    @Test
    public void searchChargeByReferenceAndStatusAndFromDate_ShouldReturnZeroIfDateIsNotInRange() throws Exception {

        ChargeSearchParams params = new ChargeSearchParams()
                .withTransactionType("charge")
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withReferenceLike(defaultTestCharge.getReference())
                .withExternalChargeState(EXTERNAL_CREATED.getStatus())
                .withFromDate(ZonedDateTime.parse(TO_DATE));

        // when
        List<Transaction> transactions = transactionDao.findAllBy(params);

        assertThat(transactions.size(), is(0));
    }

    @Test
    public void searchChargeByReferenceAndStatusAndToDate_ShouldReturnZeroIfToDateIsNotInRange() throws Exception {

        ChargeSearchParams params = new ChargeSearchParams()
                .withTransactionType("charge")
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withReferenceLike(defaultTestCharge.getReference())
                .withExternalChargeState(EXTERNAL_CREATED.getStatus())
                .withToDate(ZonedDateTime.parse(FROM_DATE));

        List<Transaction> transactions = transactionDao.findAllBy(params);

        assertThat(transactions.size(), is(0));
    }

    private Matcher<? super List<ChargeEventEntity>> shouldIncludeStatus(ChargeStatus... expectedStatuses) {
        return new TypeSafeMatcher<List<ChargeEventEntity>>() {
            @Override
            protected boolean matchesSafely(List<ChargeEventEntity> chargeEvents) {
                List<ChargeStatus> actualStatuses = chargeEvents.stream()
                        .map(ChargeEventEntity::getStatus)
                        .collect(toList());
                return actualStatuses.containsAll(asList(expectedStatuses));
            }

            @Override
            public void describeTo(Description description) {
                description.appendText(String.format("does not contain [%s]", expectedStatuses));
            }
        };
    }

    private void assertDateMatch(String createdDateString) {
        assertDateMatch(DateTimeUtils.toUTCZonedDateTime(createdDateString).get());
    }

    private void assertDateMatch(ZonedDateTime createdDateTime) {
        assertThat(createdDateTime, within(1, ChronoUnit.MINUTES, ZonedDateTime.now()));
    }
}
