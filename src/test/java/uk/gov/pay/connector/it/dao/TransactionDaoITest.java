package uk.gov.pay.connector.it.dao;

import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.commons.model.SupportedLanguage;
import uk.gov.pay.connector.dao.ChargeSearchParams;
import uk.gov.pay.connector.dao.TransactionDao;
import uk.gov.pay.connector.it.dao.DatabaseFixtures.TestCharge;
import uk.gov.pay.connector.model.CardHolderName;
import uk.gov.pay.connector.model.LastDigitsCardNumber;
import uk.gov.pay.connector.model.ServicePaymentReference;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.RefundStatus;
import uk.gov.pay.connector.model.domain.Transaction;
import uk.gov.pay.connector.util.DateTimeUtils;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

import static java.time.ZonedDateTime.now;
import static java.util.Collections.singletonList;
import static org.exparity.hamcrest.date.ZonedDateTimeMatchers.within;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.it.dao.DatabaseFixtures.withDatabaseTestHelper;
import static uk.gov.pay.connector.model.TransactionType.PAYMENT;
import static uk.gov.pay.connector.model.TransactionType.REFUND;
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
    private static final String REFUND_USER_EXTERNAL_ID = "user";
    private TransactionDao transactionDao;
    private DatabaseFixtures.TestAccount defaultTestAccount;

    @Before
    public void setupAccountWithOneChargeAndRefundForTheCharge() {
        transactionDao= env.getInstance(TransactionDao.class);

        defaultTestAccount =
                withDatabaseTestHelper(databaseTestHelper)
                        .aTestAccount()
                        .insert();
    }

    @Test
    public void searchChargesByGatewayAccountIdOnly()  {

        // given
        DatabaseFixtures.TestCharge testCharge = insertNewChargeWithId(1L, now());
        DatabaseFixtures.TestCardDetails testCardDetails = updateCardDetailsForCharge(testCharge);
        DatabaseFixtures.TestRefund testRefund = insertNewRefundForCharge(testCharge, REFUND_USER_EXTERNAL_ID, 2L, now().plusSeconds(1));

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
        assertThat(transactionRefund.getUserExternalId(), is(REFUND_USER_EXTERNAL_ID));
        assertThat(transactionRefund.getCardBrandLabel(), is("Visa")); // read from card types table which is populated by the card_types.csv seed data
        assertThat(transactionRefund.getLanguage(), is(testCharge.getLanguage()));
        assertDateMatch(transactionRefund.getCreatedDate().toString());

        Transaction transactionCharge = transactions.get(1);
        assertThat(transactionCharge.getTransactionType(), is("charge"));
        assertThat(transactionCharge.getChargeId(), is(testCharge.getChargeId()));
        assertThat(transactionCharge.getAmount(), is(testCharge.getAmount()));
        assertThat(transactionCharge.getReference(), is(testCharge.getReference()));
        assertThat(transactionCharge.getDescription(), is(testCharge.getDescription()));
        assertThat(transactionCharge.getStatus(), is(testCharge.getChargeStatus().toString()));
        assertThat(transactionCharge.getCardBrand(), is(testCardDetails.getCardBrand()));
        assertThat(transactionCharge.getUserExternalId(), is(nullValue()));
        assertThat(transactionCharge.getLanguage(), is(testCharge.getLanguage()));
        assertDateMatch(transactionCharge.getCreatedDate().toString());
    }

    @Test
    public void searchChargesByFullEmailMatch()  {

        // given
        DatabaseFixtures.TestCharge testCharge = insertNewChargeWithId(1L, now().plusHours(1));
        DatabaseFixtures.TestRefund testRefund = insertNewRefundForCharge(testCharge, REFUND_USER_EXTERNAL_ID,2L, now().plusHours(2));
        ChargeSearchParams params = new ChargeSearchParams()
                .withEmailLike(testCharge.getEmail());

        // when
        List<Transaction> transactions = transactionDao.findAllBy(defaultTestAccount.getAccountId(), params);

        // then
        assertThat(transactions.size(), is(2));

        assertThat(transactions.get(0).getAmount(), is(testRefund.getAmount()));
        assertThat(transactions.get(0).getTransactionType(), is("refund"));
        assertThat(transactions.get(0).getUserExternalId(), is(REFUND_USER_EXTERNAL_ID));
        assertThat(transactions.get(1).getAmount(), is(testCharge.getAmount()));
        assertThat(transactions.get(1).getTransactionType(), is("charge"));
        assertThat(transactions.get(1).getUserExternalId(), is(nullValue()));
    }

    @Test
    public void searchChargesByPartialEmailMatch()  {

        // given
        DatabaseFixtures.TestCharge testCharge = insertNewChargeWithId(1L, now().plusHours(1));
        DatabaseFixtures.TestRefund testRefund = insertNewRefundForCharge(testCharge, REFUND_USER_EXTERNAL_ID,2L, now().plusHours(2));
        ChargeSearchParams params = new ChargeSearchParams()
                .withEmailLike("alice");

        // when
        List<Transaction> transactions = transactionDao.findAllBy(defaultTestAccount.getAccountId(), params);

        // then
        assertThat(transactions.size(), is(2));

        assertThat(transactions.get(0).getAmount(), is(testRefund.getAmount()));
        assertThat(transactions.get(0).getTransactionType(), is("refund"));
        assertThat(transactions.get(0).getUserExternalId(), is(REFUND_USER_EXTERNAL_ID));
        assertThat(transactions.get(1).getAmount(), is(testCharge.getAmount()));
        assertThat(transactions.get(1).getTransactionType(), is("charge"));
        assertThat(transactions.get(1).getUserExternalId(), is(nullValue()));
    }

    @Test
    public void searchChargesByCardBrandOnly()  {

        // given
        DatabaseFixtures.TestCharge testCharge = insertNewChargeWithId(1L, now().plusHours(1));
        DatabaseFixtures.TestCardDetails testCardDetails = updateCardDetailsForCharge(testCharge);
        DatabaseFixtures.TestRefund testRefund = insertNewRefundForCharge(testCharge, REFUND_USER_EXTERNAL_ID,2L, now().plusHours(2));
        ChargeSearchParams params = new ChargeSearchParams()
                .withCardBrand(testCardDetails.getCardBrand());

        // when
        List<Transaction> transactions = transactionDao.findAllBy(defaultTestAccount.getAccountId(), params);

        // then
        assertThat(transactions.size(), is(2));

        assertThat(transactions.get(0).getAmount(), is(testRefund.getAmount()));
        assertThat(transactions.get(0).getTransactionType(), is("refund"));
        assertThat(transactions.get(0).getUserExternalId(), is(REFUND_USER_EXTERNAL_ID));
        assertThat(transactions.get(1).getAmount(), is(testCharge.getAmount()));
        assertThat(transactions.get(1).getTransactionType(), is("charge"));
        assertThat(transactions.get(1).getUserExternalId(), is(nullValue()));
    }

    @Test
    public void searchChargesByMultipleCardBrandOnly()  {

        // given
        String visa = "visa";
        String masterCard = "master-card";
        DatabaseFixtures.TestCharge testCharge1 = insertNewChargeWithId(1L, now().plusHours(1));
        updateCardDetailsForCharge(testCharge1, visa);
        DatabaseFixtures.TestRefund testRefund1 = insertNewRefundForCharge(testCharge1, REFUND_USER_EXTERNAL_ID,2L, now().plusHours(2));

        DatabaseFixtures.TestCharge testCharge2 = insertNewChargeWithId(3L, now().plusHours(3));
        updateCardDetailsForCharge(testCharge2, visa);
        DatabaseFixtures.TestRefund testRefund2 = insertNewRefundForCharge(testCharge2, REFUND_USER_EXTERNAL_ID,4L, now().plusHours(4));

        ChargeSearchParams params = new ChargeSearchParams()
                .withCardBrands(Arrays.asList(visa, masterCard));

        // when
        List<Transaction> transactions = transactionDao.findAllBy(defaultTestAccount.getAccountId(), params);

        // then
        assertThat(transactions.size(), is(4));

        assertThat(transactions.get(0).getAmount(), is(testRefund2.getAmount()));
        assertThat(transactions.get(0).getTransactionType(), is("refund"));
        assertThat(transactions.get(0).getUserExternalId(), is(REFUND_USER_EXTERNAL_ID));
        assertThat(transactions.get(1).getAmount(), is(testCharge2.getAmount()));
        assertThat(transactions.get(1).getTransactionType(), is("charge"));
        assertThat(transactions.get(1).getUserExternalId(), is(nullValue()));
        assertThat(transactions.get(2).getAmount(), is(testRefund1.getAmount()));
        assertThat(transactions.get(2).getTransactionType(), is("refund"));
        assertThat(transactions.get(2).getUserExternalId(), is(REFUND_USER_EXTERNAL_ID));
        assertThat(transactions.get(3).getAmount(), is(testCharge1.getAmount()));
        assertThat(transactions.get(3).getTransactionType(), is("charge"));
        assertThat(transactions.get(3).getUserExternalId(), is(nullValue()));
    }

    @Test
    public void searchChargesWithDefaultSizeAndPage_shouldGetChargesInCreationDateOrder()  {

        // given
        DatabaseFixtures.TestCharge charge1 = insertNewChargeWithId(1L, now().plusHours(1));
        insertNewRefundForCharge(charge1, REFUND_USER_EXTERNAL_ID,2L, now().plusHours(2));
        DatabaseFixtures.TestCharge charge2 = insertNewChargeWithId(3L, now().plusHours(3));
        insertNewRefundForCharge(charge2, REFUND_USER_EXTERNAL_ID,4L, now().plusHours(4));
        insertNewRefundForCharge(charge2, REFUND_USER_EXTERNAL_ID,5L, now().plusHours(5));
        DatabaseFixtures.TestCharge charge3 = insertNewChargeWithId(6L, now().plusHours(6));

        ChargeSearchParams params = new ChargeSearchParams();

        // when
        List<Transaction> transactions = transactionDao.findAllBy(defaultTestAccount.getAccountId(), params);

        // then
        assertThat(transactions.size(), is(6));
        assertThat(transactions.get(0).getExternalId(), is(charge3.getExternalChargeId()));
        assertThat(transactions.get(0).getTransactionType(), is("charge"));
        assertThat(transactions.get(0).getAmount(), is(6L));
        assertThat(transactions.get(0).getUserExternalId(), is(nullValue()));

        assertThat(transactions.get(1).getExternalId(), is(charge2.getExternalChargeId()));
        assertThat(transactions.get(1).getTransactionType(), is("refund"));
        assertThat(transactions.get(1).getAmount(), is(5L));
        assertThat(transactions.get(1).getUserExternalId(), is(REFUND_USER_EXTERNAL_ID));

        assertThat(transactions.get(2).getExternalId(), is(charge2.getExternalChargeId()));
        assertThat(transactions.get(2).getTransactionType(), is("refund"));
        assertThat(transactions.get(2).getAmount(), is(4L));
        assertThat(transactions.get(2).getUserExternalId(), is(REFUND_USER_EXTERNAL_ID));

        assertThat(transactions.get(3).getExternalId(), is(charge2.getExternalChargeId()));
        assertThat(transactions.get(3).getTransactionType(), is("charge"));
        assertThat(transactions.get(3).getAmount(), is(3L));
        assertThat(transactions.get(3).getUserExternalId(), is(nullValue()));
        
        assertThat(transactions.get(4).getExternalId(), is(charge1.getExternalChargeId()));
        assertThat(transactions.get(4).getTransactionType(), is("refund"));
        assertThat(transactions.get(4).getAmount(), is(2L));
        assertThat(transactions.get(4).getUserExternalId(), is(REFUND_USER_EXTERNAL_ID));
        
        assertThat(transactions.get(5).getExternalId(), is(charge1.getExternalChargeId()));
        assertThat(transactions.get(5).getTransactionType(), is("charge"));
        assertThat(transactions.get(5).getAmount(), is(1L));
        assertThat(transactions.get(5).getUserExternalId(), is(nullValue()));
    }

    @Test
    public void searchChargesWithSizeAndPageSetShouldGetTransactionsInCreationDateOrder()  {

        // given
        DatabaseFixtures.TestCharge charge1 = insertNewChargeWithId(1L, now().plusHours(1));
        insertNewRefundForCharge(charge1, REFUND_USER_EXTERNAL_ID,2L, now().plusHours(2));
        DatabaseFixtures.TestCharge charge2 = insertNewChargeWithId(3L, now().plusHours(3));
        insertNewRefundForCharge(charge2, REFUND_USER_EXTERNAL_ID,4L, now().plusHours(4));
        insertNewRefundForCharge(charge2, REFUND_USER_EXTERNAL_ID,5L, now().plusHours(5));
        DatabaseFixtures.TestCharge charge3 = insertNewChargeWithId(6L, now().plusHours(6));
        DatabaseFixtures.TestCharge charge4 = insertNewChargeWithId(6L, now().plusHours(7));
        insertNewRefundForCharge(charge3, REFUND_USER_EXTERNAL_ID,5L, now().plusHours(8));

        // when
        ChargeSearchParams params = new ChargeSearchParams()
                .withPage(1L)
                .withDisplaySize(3L);

        List<Transaction> transactions = transactionDao.findAllBy(defaultTestAccount.getAccountId(), params);
        // then
        assertThat(transactions.size(), is(3));

        assertThat(transactions.get(0).getExternalId(), is(charge3.getExternalChargeId()));
        assertThat(transactions.get(0).getTransactionType(), is("refund"));
        assertThat(transactions.get(0).getUserExternalId(), is(REFUND_USER_EXTERNAL_ID));

        assertThat(transactions.get(1).getExternalId(), is(charge4.getExternalChargeId()));
        assertThat(transactions.get(1).getTransactionType(), is("charge"));
        assertThat(transactions.get(1).getUserExternalId(), is(nullValue()));

        assertThat(transactions.get(2).getExternalId(), is(charge3.getExternalChargeId()));
        assertThat(transactions.get(2).getTransactionType(), is("charge"));
        assertThat(transactions.get(2).getUserExternalId(), is(nullValue()));

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
        assertThat(transactions.get(0).getUserExternalId(), is(REFUND_USER_EXTERNAL_ID));

        assertThat(transactions.get(1).getExternalId(), is(charge2.getExternalChargeId()));
        assertThat(transactions.get(1).getTransactionType(), is("refund"));
        assertThat(transactions.get(1).getAmount(), is(4L));
        assertThat(transactions.get(1).getUserExternalId(), is(REFUND_USER_EXTERNAL_ID));

        assertThat(transactions.get(2).getExternalId(), is(charge2.getExternalChargeId()));
        assertThat(transactions.get(2).getTransactionType(), is("charge"));
        assertThat(transactions.get(2).getUserExternalId(), is(nullValue()));


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
        assertThat(transactions.get(0).getUserExternalId(), is(REFUND_USER_EXTERNAL_ID));

        assertThat(transactions.get(1).getExternalId(), is(charge1.getExternalChargeId()));
        assertThat(transactions.get(1).getTransactionType(), is("charge"));
        assertThat(transactions.get(1).getUserExternalId(), is(nullValue()));

    }

    @Test
    public void shouldGetExpectedTotalCount() {

        // given
        DatabaseFixtures.TestCharge charge1 = insertNewChargeWithId(1L, now().plusHours(1));
        insertNewRefundForCharge(charge1, REFUND_USER_EXTERNAL_ID,2L, now().plusHours(2));
        DatabaseFixtures.TestCharge charge2 = insertNewChargeWithId(3L, now().plusHours(3));
        insertNewRefundForCharge(charge2, REFUND_USER_EXTERNAL_ID,4L, now().plusHours(4));
        insertNewRefundForCharge(charge2, REFUND_USER_EXTERNAL_ID,5L, now().plusHours(5));
        DatabaseFixtures.TestCharge charge3 = insertNewChargeWithId(6L, now().plusHours(6));
        insertNewChargeWithId(6L, now().plusHours(7));
        insertNewRefundForCharge(charge3, REFUND_USER_EXTERNAL_ID,5L, now().plusHours(8));

        ChargeSearchParams params = new ChargeSearchParams()
                .withPage(1L)
                .withDisplaySize(2L);

        // when
        Long count = transactionDao.getTotalFor(defaultTestAccount.getAccountId(), params);

        // then
        assertThat("total count for transactions matches", count, is(8L));

    }

    @Test
    public void searchChargesByFullReferenceOnly()  {

        // given
        DatabaseFixtures.TestCharge testCharge = insertNewChargeWithId(1L, now().plusHours(1));
        insertNewRefundForCharge(testCharge, REFUND_USER_EXTERNAL_ID,2L, now().plusHours(2));
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
        assertThat(refund.getUserExternalId(), is(REFUND_USER_EXTERNAL_ID));
        assertThat(charge.getExternalId(), is(testCharge.getExternalChargeId()));
        assertThat(charge.getTransactionType(), is("charge"));
        assertThat(charge.getUserExternalId(), is(nullValue()));
    }

    @Test
    public void searchChargesByPartialReferenceOnly()  {

        // given
        DatabaseFixtures.TestCharge testCharge = insertNewChargeWithId(1L, now().plusHours(1));
        insertNewRefundForCharge(testCharge, REFUND_USER_EXTERNAL_ID,2L, now().plusHours(2));

        ServicePaymentReference partialPaymentReference = ServicePaymentReference.of("Council Tax");

        DatabaseFixtures.TestCharge partialReferenceCharge =
                withDatabaseTestHelper(databaseTestHelper)
                        .aTestCharge()
                        .withReference(ServicePaymentReference.of(partialPaymentReference.toString() + " whatever"))
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
        assertThat(transactions.get(0).getUserExternalId(), is(nullValue()));

        assertThat(transactions.get(1).getExternalId(), is(testCharge.getExternalChargeId()));
        assertThat(transactions.get(1).getTransactionType(), is("refund"));
        assertThat(transactions.get(1).getUserExternalId(), is(REFUND_USER_EXTERNAL_ID));

        assertThat(transactions.get(2).getExternalId(), is(testCharge.getExternalChargeId()));
        assertThat(transactions.get(2).getReference(), is(testCharge.getReference()));
        assertThat(transactions.get(2).getTransactionType(), is("charge"));
        assertThat(transactions.get(2).getUserExternalId(), is(nullValue()));

    }

    @Test
    public void shouldSearchChargesByFullCardHolderName()  {

        // given
        String cardHolderName = "Mr Cardholder Bla";

        TestCharge testCharge = withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount);
        DatabaseFixtures.TestCardDetails testCardDetails = new DatabaseFixtures(databaseTestHelper)
                .validTestCardDetails()
                .withCardHolderName(cardHolderName)
                .withChargeId(testCharge.chargeId)
                .update();
        testCharge.withCardDetails(testCardDetails).insert();
        insertNewRefundForCharge(testCharge, REFUND_USER_EXTERNAL_ID,2L, now().plusHours(2));
        ChargeSearchParams params = new ChargeSearchParams()
                .withCardHolderNameLike(CardHolderName.of(cardHolderName));

        // when
        List<Transaction> transactions = transactionDao.findAllBy(defaultTestAccount.getAccountId(), params);

        // then
        assertThat(transactions.size(), is(2));

        Transaction refund = transactions.get(0);
        Transaction charge = transactions.get(1);

        assertThat(refund.getExternalId(), is(testCharge.getExternalChargeId()));
        assertThat(refund.getTransactionType(), is("refund"));
        assertThat(refund.getCardHolderName(), is(cardHolderName));
        assertThat(charge.getExternalId(), is(testCharge.getExternalChargeId()));
        assertThat(charge.getTransactionType(), is("charge"));
        assertThat(charge.getCardHolderName(), is(cardHolderName));
    }

    @Test
    public void shouldSearchChargesByPartialCardHolderName()  {

        // given
        String fullCardHolderName = "Mr Cardholder Bla";

        TestCharge testCharge = withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount);
        DatabaseFixtures.TestCardDetails testCardDetails = new DatabaseFixtures(databaseTestHelper)
                .validTestCardDetails()
                .withCardHolderName(fullCardHolderName)
                .withChargeId(testCharge.chargeId)
                .update();
        testCharge.withCardDetails(testCardDetails).insert();
        insertNewRefundForCharge(testCharge, REFUND_USER_EXTERNAL_ID,2L, now().plusHours(2));
        ChargeSearchParams params = new ChargeSearchParams()
                .withCardHolderNameLike(CardHolderName.of("bla"));

        // when
        List<Transaction> transactions = transactionDao.findAllBy(defaultTestAccount.getAccountId(), params);

        // then
        assertThat(transactions.size(), is(2));

        Transaction refund = transactions.get(0);
        Transaction charge = transactions.get(1);

        assertThat(refund.getExternalId(), is(testCharge.getExternalChargeId()));
        assertThat(refund.getTransactionType(), is("refund"));
        assertThat(refund.getCardHolderName(), is(fullCardHolderName));
        assertThat(charge.getExternalId(), is(testCharge.getExternalChargeId()));
        assertThat(charge.getTransactionType(), is("charge"));
        assertThat(charge.getCardHolderName(), is(fullCardHolderName));
    }

    @Test
    public void shouldSearchTransactionsByFullLastFourDigitsCardNumber()  {

        // given
        String lastDigitsCardNumber = "1611";

        TestCharge testCharge = withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount);
        DatabaseFixtures.TestCardDetails testCardDetails = new DatabaseFixtures(databaseTestHelper)
                .validTestCardDetails()
                .withLastDigitsOfCardNumber(lastDigitsCardNumber)
                .withChargeId(testCharge.chargeId)
                .update();
        testCharge.withCardDetails(testCardDetails).insert();
        insertNewRefundForCharge(testCharge, REFUND_USER_EXTERNAL_ID,2L, now().plusHours(2));
        ChargeSearchParams params = new ChargeSearchParams()
                .withLastDigitsCardNumber(LastDigitsCardNumber.of(lastDigitsCardNumber));

        // when
        List<Transaction> transactions = transactionDao.findAllBy(defaultTestAccount.getAccountId(), params);

        // then
        assertThat(transactions.size(), is(2));

        Transaction refund = transactions.get(0);
        Transaction charge = transactions.get(1);

        assertThat(refund.getExternalId(), is(testCharge.getExternalChargeId()));
        assertThat(refund.getTransactionType(), is("refund"));
        assertThat(refund.getLastDigitsCardNumber(), is(lastDigitsCardNumber));
        assertThat(charge.getExternalId(), is(testCharge.getExternalChargeId()));
        assertThat(charge.getTransactionType(), is("charge"));
        assertThat(charge.getLastDigitsCardNumber(), is(lastDigitsCardNumber));
    }

    @Test
    public void shouldNotMatchTransactionsByPartialLastFourDigitsCardNumber() {
        // given
        String lastDigitsCardNumber = "4321";
        TestCharge testCharge = withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount);
        DatabaseFixtures.TestCardDetails testCardDetails = new DatabaseFixtures(databaseTestHelper)
                .validTestCardDetails()
                .withLastDigitsOfCardNumber(lastDigitsCardNumber)
                .withChargeId(testCharge.chargeId)
                .update();
        testCharge.withCardDetails(testCardDetails).insert();
        ChargeSearchParams params = new ChargeSearchParams()
                .withLastDigitsCardNumber(LastDigitsCardNumber.of("432"));

        // when
        List<Transaction> transactions = transactionDao.findAllBy(defaultTestAccount.getAccountId(), params);

        // then
        assertThat(transactions.size(), is(0));
    }
    @Test
    public void searchChargesByReferenceAndEmail_with_under_score() {
        // since '_' have special meaning in like queries of postgres this was resulting in undesired results
        withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withReference(ServicePaymentReference.of("under_score_ref"))
                .withEmail("under_score@mail.test")
                .insert();

        withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withReference(ServicePaymentReference.of("understand"))
                .withEmail("undertaker@mail.test")
                .insert();

        ChargeSearchParams params = new ChargeSearchParams()
                .withReferenceLike(ServicePaymentReference.of("under_"))
                .withEmailLike("under_");

        // when
        List<Transaction> transactions = transactionDao.findAllBy(defaultTestAccount.getAccountId(), params);

        // then
        assertThat(transactions.size(), is(1));

        Transaction transaction = transactions.get(0);
        assertThat(transaction.getReference(), is(ServicePaymentReference.of("under_score_ref")));
        assertThat(transaction.getEmail(), is("under_score@mail.test"));
    }

    @Test
    public void searchChargesByReferenceWithPercentSign()  {
        // since '%' have special meaning in like queries of postgres this was resulting in undesired results
        withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withReference(ServicePaymentReference.of("percent%ref"))
                .insert();

        withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withReference(ServicePaymentReference.of("percentref"))
                .insert();

        ChargeSearchParams params = new ChargeSearchParams()
                .withReferenceLike(ServicePaymentReference.of("percent%"));

        // when
        List<Transaction> transactions = transactionDao.findAllBy(defaultTestAccount.getAccountId(), params);

        // then
        assertThat(transactions.size(), is(1));

        Transaction transaction = transactions.get(0);
        assertThat(transaction.getReference(), is(ServicePaymentReference.of("percent%ref")));
    }

    @Test
    public void searchTransactionsByReferenceWithBackslash() {
        // since '\' is an escape character in postgres (and java) this was resulting in undesired results
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withReference(ServicePaymentReference.of("backslash\\ref"))
                .insert();

        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withReference(ServicePaymentReference.of("backslashref"))
                .insert();

        ChargeSearchParams params = new ChargeSearchParams()
                .withReferenceLike(ServicePaymentReference.of("backslash\\ref"));

        // when
        List<Transaction> transactions = transactionDao.findAllBy(defaultTestAccount.getAccountId(), params);

        // then
        assertThat(transactions.size(), is(1));

        Transaction transaction = transactions.get(0);
        assertThat(transaction.getReference(), is(ServicePaymentReference.of("backslash\\ref")));
    }

    @Test
    public void searchTransactionByReferenceAndEmailShouldBeCaseInsensitive()  {
        // fix that the reference and email searches should be case insensitive
        withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withEmail("email-id@mail.test")
                .withReference(ServicePaymentReference.of("case-Insensitive-ref"))
                .insert();

        withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withEmail("EMAIL-ID@MAIL.TEST")
                .withReference(ServicePaymentReference.of("Case-inSENSITIVE-Ref"))
                .insert();

        ChargeSearchParams params = new ChargeSearchParams()
                .withReferenceLike(ServicePaymentReference.of("cASe-insEnsiTIve"))
                .withEmailLike("EMAIL-ID@mail.test");

        // when
        List<Transaction> transactions = transactionDao.findAllBy(defaultTestAccount.getAccountId(), params);

        // then
        assertThat(transactions.size(), is(2));

        Transaction transaction = transactions.get(0);
        assertThat(transaction.getReference(), is(ServicePaymentReference.of("Case-inSENSITIVE-Ref")));
        assertThat(transaction.getEmail(), is("EMAIL-ID@MAIL.TEST"));

        transaction = transactions.get(1);
        assertThat(transaction.getReference(), is(ServicePaymentReference.of("case-Insensitive-ref")));
        assertThat(transaction.getEmail(), is("email-id@mail.test"));
    }

    @Test
    public void aBasicTestAgainstSqlInjection()  {
        // given
        withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withReference(ServicePaymentReference.of("Test reference"))
                .insert();
        ChargeSearchParams params = new ChargeSearchParams()
                .withReferenceLike(ServicePaymentReference.of("reference"));
        // when passed in a simple reference string
        List<Transaction> transactions = transactionDao.findAllBy(defaultTestAccount.getAccountId(), params);
        // then it fetches a single result
        assertThat(transactions.size(), is(1));

        // when passed in a non existent reference with an sql injected string
        String sqlInjectionReferenceString = "reffff%' or 1=1 or c.reference like '%1";
        params = new ChargeSearchParams()
                .withReferenceLike(ServicePaymentReference.of(sqlInjectionReferenceString));
        transactions = transactionDao.findAllBy(defaultTestAccount.getAccountId(), params);
        // then it fetches no result
        // with a typical sql injection vulnerable query doing this should fetch all results
        assertThat(transactions.size(), is(0));
    }


    @Test
    public void searchTransactionByReferenceAndLegacyStatusOnly()  {
        // given
        DatabaseFixtures.TestCharge testCharge = insertNewChargeWithId(1L, now());
        DatabaseFixtures.TestCardDetails testCardDetails = updateCardDetailsForCharge(testCharge);
        DatabaseFixtures.TestRefund testRefund = insertNewRefundForCharge(testCharge, REFUND_USER_EXTERNAL_ID,2L, now().plusSeconds(1));

        ChargeSearchParams params = new ChargeSearchParams()
                .withReferenceLike(testCharge.getReference())
                .addExternalChargeStates(singletonList(EXTERNAL_CREATED.getStatus()));

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
    public void searchTransactionByReferenceAndStatusAndFromDateAndToDate()  {

        // given
        DatabaseFixtures.TestCharge testCharge = insertNewChargeWithId(1L, now());
        DatabaseFixtures.TestCardDetails testCardDetails = updateCardDetailsForCharge(testCharge);
        DatabaseFixtures.TestRefund testRefund = insertNewRefundForCharge(testCharge, REFUND_USER_EXTERNAL_ID,2L, now().plusSeconds(1));

        ChargeSearchParams params = new ChargeSearchParams()
                .withReferenceLike(testCharge.getReference())
                .addExternalChargeStates(singletonList(EXTERNAL_CREATED.getStatus()))
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
    public void searchChargeByReferenceAndStatusAndFromDate()  {

        // given
        DatabaseFixtures.TestCharge testCharge = insertNewChargeWithId(1L, now());
        DatabaseFixtures.TestCardDetails testCardDetails = updateCardDetailsForCharge(testCharge);
        insertNewRefundForCharge(testCharge, REFUND_USER_EXTERNAL_ID,2L, now().plusSeconds(2));

        ChargeSearchParams params = new ChargeSearchParams()
                .withTransactionType(PAYMENT)
                .withReferenceLike(testCharge.getReference())
                .addExternalChargeStates(singletonList(EXTERNAL_CREATED.getStatus()))
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
    public void searchRefundByReferenceAndRefundStatusAndFromDate()  {

        // given
        DatabaseFixtures.TestCharge testCharge = insertNewChargeWithId(1L, now());
        DatabaseFixtures.TestCardDetails testCardDetails = updateCardDetailsForCharge(testCharge);
        DatabaseFixtures.TestRefund testRefund = insertNewRefundForCharge(testCharge, REFUND_USER_EXTERNAL_ID,2L, now().plusSeconds(2));

        ChargeSearchParams params = new ChargeSearchParams()
                .withTransactionType(REFUND)
                .withReferenceLike(testCharge.getReference())
                .addExternalRefundStates(singletonList(EXTERNAL_SUBMITTED.getStatus()))
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
                .addExternalChargeStates(singletonList(EXTERNAL_STARTED.getStatus()));

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
                .addExternalChargeStates(singletonList(EXTERNAL_STARTED.getStatus()))
                .addExternalChargeStates(singletonList(EXTERNAL_CREATED.getStatus()));

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
                .withTransactionType(PAYMENT)
                .addExternalChargeStates(singletonList(EXTERNAL_STARTED.getStatus()));

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
                .addExternalRefundStates(singletonList(EXTERNAL_SUCCESS.getStatus()));

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
                .addExternalRefundStates(singletonList(EXTERNAL_SUBMITTED.getStatus()))
                .addExternalRefundStates(singletonList(EXTERNAL_SUCCESS.getStatus()));

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
                .withTransactionType(REFUND)
                .addExternalRefundStates(singletonList(EXTERNAL_SUCCESS.getStatus()));

        // when
        List<Transaction> transactions = transactionDao.findAllBy(defaultTestAccount.getAccountId(), params);

        // then
        assertThat(transactions.size(), is(1));
        assertThat(transactions.get(0).getTransactionType(), is("refund"));
        assertThat(transactions.get(0).getStatus(), is(RefundStatus.REFUNDED.getValue()));
    }

    @Test
    public void searchChargeByReferenceAndStatusAndEmailAndCardBrandAndFromDateAndToDate()  {
        // given
        DatabaseFixtures.TestCharge testCharge = insertNewChargeWithId(1L, now());
        DatabaseFixtures.TestCardDetails testCardDetails = updateCardDetailsForCharge(testCharge);
        insertNewRefundForCharge(testCharge, REFUND_USER_EXTERNAL_ID, 2L, now().plusSeconds(2));

        ChargeSearchParams params = new ChargeSearchParams()
                .withTransactionType(PAYMENT)
                .withReferenceLike(testCharge.getReference())
                .addExternalChargeStates(singletonList(EXTERNAL_CREATED.getStatus()))
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
        assertThat(charge.getUserExternalId(), is(nullValue()));
        assertDateMatch(charge.getCreatedDate().toString());
    }

    @Test
    public void searchChargeByReferenceAndStatusAndToDate()  {

        // given
        DatabaseFixtures.TestCharge testCharge = insertNewChargeWithId(1L, now());
        DatabaseFixtures.TestCardDetails testCardDetails = updateCardDetailsForCharge(testCharge);
        insertNewRefundForCharge(testCharge, REFUND_USER_EXTERNAL_ID, 2L, now().plusSeconds(2));

        ChargeSearchParams params = new ChargeSearchParams()
                .withTransactionType(PAYMENT)
                .withReferenceLike(testCharge.getReference())
                .addExternalChargeStates(singletonList(EXTERNAL_CREATED.getStatus()))
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
        assertThat(charge.getUserExternalId(), is(nullValue()));

        assertDateMatch(charge.getCreatedDate().toString());
    }

    @Test
    public void searchTransactionByReferenceAndStatusAndFromDate_ShouldReturnZeroIfDateIsNotInRange()  {

        // given
        DatabaseFixtures.TestCharge testCharge = insertNewChargeWithId(1L, now().plusHours(1));
        insertNewRefundForCharge(testCharge, REFUND_USER_EXTERNAL_ID, 2L, now().plusHours(2));

        ChargeSearchParams params = new ChargeSearchParams()
                .withReferenceLike(testCharge.getReference())
                .addExternalChargeStates(singletonList(EXTERNAL_CREATED.getStatus()))
                .withFromDate(ZonedDateTime.parse(TO_DATE));

        // when
        List<Transaction> transactions = transactionDao.findAllBy(defaultTestAccount.getAccountId(), params);

        // then
        assertThat(transactions.size(), is(0));
    }

    @Test
    public void searchTransactionByReferenceAndStatusAndToDate_ShouldReturnZeroIfToDateIsNotInRange()  {

        // given
        DatabaseFixtures.TestCharge testCharge = insertNewChargeWithId(1L, now().plusHours(1));
        insertNewRefundForCharge(testCharge, REFUND_USER_EXTERNAL_ID, 2L, now().plusHours(2));

        ChargeSearchParams params = new ChargeSearchParams()
                .withReferenceLike(testCharge.getReference())
                .addExternalChargeStates(singletonList(EXTERNAL_CREATED.getStatus()))
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
                        .withReference(ServicePaymentReference.of(DEFAULT_TEST_CHARGE_REFERENCE))
                        .withAmount(amount)
                        .withTestAccount(defaultTestAccount)
                        .withCreatedDate(creationDate)
                        .withLanguage(SupportedLanguage.ENGLISH)
                        .insert();
    }

    private DatabaseFixtures.TestCardDetails updateCardDetailsForCharge(DatabaseFixtures.TestCharge charge) {
        return updateCardDetailsForCharge(charge, "visa");
    }

    private DatabaseFixtures.TestCardDetails updateCardDetailsForCharge(DatabaseFixtures.TestCharge charge, String cardBrand) {
        return new DatabaseFixtures(databaseTestHelper)
                .validTestCardDetails()
                .withChargeId(charge.chargeId)
                .withCardBrand(cardBrand)
                .update();
    }

    private DatabaseFixtures.TestRefund insertNewRefundForCharge(DatabaseFixtures.TestCharge charge, String userExternalId, long amount, ZonedDateTime creationDate) {
        return
                withDatabaseTestHelper(databaseTestHelper)
                        .aTestRefund()
                        .withAmount(amount)
                        .withTestCharge(charge)
                        .withCreatedDate(creationDate)
                        .withSubmittedBy(userExternalId)
                        .insert();
    }
}
