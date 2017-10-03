package uk.gov.pay.connector.it.dao;

import static java.time.ZonedDateTime.now;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.exparity.hamcrest.date.ZonedDateTimeMatchers.within;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static uk.gov.pay.connector.model.api.ExternalChargeState.EXTERNAL_CREATED;
import static uk.gov.pay.connector.model.api.ExternalChargeState.EXTERNAL_STARTED;
import static uk.gov.pay.connector.model.domain.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.model.spike.TransactionEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.model.spike.TransactionEventEntity.TransactionStatus.AUTHORISATION_READY;
import static uk.gov.pay.connector.model.spike.TransactionEventEntity.TransactionStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.model.spike.TransactionEventEntity.TransactionStatus.CAPTURE_READY;
import static uk.gov.pay.connector.model.spike.TransactionEventEntity.TransactionStatus.CREATED;
import static uk.gov.pay.connector.model.spike.TransactionEventEntity.TransactionStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.model.spike.TransactionEventEntity.TransactionStatus.SYSTEM_CANCELLED;

import com.google.common.collect.Lists;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang.RandomStringUtils;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.hamcrest.core.Is;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import uk.gov.pay.connector.dao.ChargeSearchParams;
import uk.gov.pay.connector.dao.PaymentRequestDao;
import uk.gov.pay.connector.dao.TransactionDao;
import uk.gov.pay.connector.it.dao.DatabaseFixtures.TestChargeNew;
import uk.gov.pay.connector.it.dao.DatabaseFixtures.TestPaymentRequest;
import uk.gov.pay.connector.it.dao.DatabaseFixtures.TestRefundNew;
import uk.gov.pay.connector.model.domain.Auth3dsDetailsEntity;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity.Type;
import uk.gov.pay.connector.model.spike.ChargeEntityNew;
import uk.gov.pay.connector.model.spike.PaymentRequestEntity;
import uk.gov.pay.connector.model.spike.PaymentRequestEntityFixture;
import uk.gov.pay.connector.model.spike.RefundEntityNew;
import uk.gov.pay.connector.model.spike.TransactionEntity;
import uk.gov.pay.connector.model.spike.TransactionEntity.TransactionOperation;
import uk.gov.pay.connector.model.spike.TransactionEntityFixture;
import uk.gov.pay.connector.model.spike.TransactionEventEntity;
import uk.gov.pay.connector.model.spike.TransactionEventEntity.TransactionStatus;
import uk.gov.pay.connector.util.DateTimeUtils;

public class TransactionDaoITest extends DaoITestBase {

    private static final String FROM_DATE = "2016-01-01T01:00:00Z";
    private static final String TO_DATE = "2026-01-08T01:00:00Z";
    private static final String DESCRIPTION = "Test description";

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    private TransactionDao transactionDao;
    private PaymentRequestDao paymentRequestDao;

    private DatabaseFixtures.TestAccount defaultTestAccount;
    private DatabaseFixtures.TestRefundNew defaultTestRefundNew;
    private DatabaseFixtures.TestCharge defaultTestCharge;
    private DatabaseFixtures.TestChargeNew defaultTestChargeNew;
    private DatabaseFixtures.TestPaymentRequest defaultTestPaymentRequest;
    private DatabaseFixtures.TestCardDetails defaultTestCardDetails;

    @Before
    public void setUp() throws Exception {
        paymentRequestDao = env.getInstance(PaymentRequestDao.class);
        transactionDao = env.getInstance(TransactionDao.class);
        defaultTestCardDetails = new DatabaseFixtures(databaseTestHelper).validTestCardDetails();
        insertTestAccount();
    }

    @Test
    public void searchTransactionsByGatewayAccountIdOnly() throws Exception {
        // given
        insertTestChargeNew();
        ChargeSearchParams params = new ChargeSearchParams()
            .withGatewayAccountId(defaultTestAccount.getAccountId())
            .withTransactionOperation(TransactionOperation.CHARGE);

        // when
        List<TransactionEntity> transactions = transactionDao.findAllBy(params);

        // then
        assertThat(transactions.size(), is(1));
        TransactionEntity transaction = transactions.get(0);
        assertThat(transaction.getId(), is(defaultTestChargeNew.getTransactionId()));
        assertThat(transaction.getAmount(), is(defaultTestChargeNew.getAmount()));
        assertThat(transaction.getStatus(), is(defaultTestChargeNew.getTransactionStatus().toString()));
        assertThat(transaction.getOperation(), is(TransactionOperation.CHARGE.toString()));
        assertThat(transaction.getPaymentRequest().getGatewayAccount().getId(), is(defaultTestPaymentRequest.gatewayAccountId));
        assertDateMatch(transaction.getCreatedDate().toString());
    }

    @Test
    public void searchChargesByFullEmailMatch() throws Exception {
        // given
        insertTestChargeNew();
        ChargeSearchParams params = new ChargeSearchParams()
            .withEmailLike(defaultTestChargeNew.getEmail())
            .withTransactionOperation(TransactionOperation.CHARGE);

        // when
        List<TransactionEntity> charges = transactionDao.findAllBy(params);

        // then
        assertThat(charges.size(), is(1));
        ChargeEntityNew charge = (ChargeEntityNew) charges.get(0);
        assertThat(charge.getId(), is(defaultTestChargeNew.getTransactionId()));
        assertThat(charge.getAmount(), is(defaultTestChargeNew.getAmount()));
        assertThat(charge.getOperation(), is(TransactionOperation.CHARGE.toString()));
        assertThat(charge.getStatus(), is(defaultTestChargeNew.getTransactionStatus().toString()));
        assertThat(charge.getEmail(), is(defaultTestChargeNew.getEmail()));
        assertThat(charge.getReference(), is(defaultTestPaymentRequest.getReference()));
        assertThat(charge.getDescription(), is(defaultTestPaymentRequest.getDescription()));
        assertDateMatch(charge.getCreatedDate().toString());
    }

    @Test
    public void searchChargesByPartialEmailMatch() throws Exception {
        // given
        insertTestChargeNew();
        ChargeSearchParams params = new ChargeSearchParams()
            .withEmailLike("aaa")
            .withTransactionOperation(TransactionOperation.CHARGE);

        // when
        List<TransactionEntity> charges = transactionDao.findAllBy(params);

        // then
        assertThat(charges.size(), is(1));
        ChargeEntityNew charge = (ChargeEntityNew) charges.get(0);
        assertThat(charge.getId(), is(defaultTestChargeNew.getTransactionId()));
        assertThat(charge.getAmount(), is(defaultTestChargeNew.getAmount()));
        assertThat(charge.getOperation(), is(TransactionOperation.CHARGE.toString()));
        assertThat(charge.getStatus(), is(defaultTestChargeNew.getTransactionStatus().toString()));
        assertThat(charge.getEmail(), is(defaultTestChargeNew.getEmail()));
        assertThat(charge.getReference(), is(defaultTestPaymentRequest.getReference()));
        assertThat(charge.getDescription(), is(defaultTestPaymentRequest.getDescription()));

        assertDateMatch(charge.getCreatedDate().toString());
    }

    @Test
    public void searchChargesByCardBrandOnly() throws Exception {
        // given
        insertTestChargeNew();
        ChargeSearchParams params = new ChargeSearchParams()
            .withCardBrand(defaultTestCardDetails.getCardBrand())
            .withTransactionOperation(TransactionOperation.CHARGE);


        // when
        List<TransactionEntity> charges = transactionDao.findAllBy(params);

        // then
        assertThat(charges.size(), is(1));
        ChargeEntityNew charge = (ChargeEntityNew) charges.get(0);
        assertThat(charge.getId(), is(defaultTestChargeNew.getTransactionId()));
        assertThat(charge.getStatus(), is(defaultTestChargeNew.getTransactionStatus().toString()));
        assertThat(charge.getEmail(), is(defaultTestChargeNew.getEmail()));
        assertThat(charge.getReference(), is(defaultTestPaymentRequest.getReference()));
        assertThat(charge.getDescription(), is(defaultTestPaymentRequest.getDescription()));
        assertThat(charge.getCardDetails().getCardBrand(), is(defaultTestCardDetails.getCardBrand()));

        assertDateMatch(charge.getCreatedDate().toString());
    }

    @Test
    public void searchChargesWithDefaultSizeAndPage_shouldGetChargesInCreationDateOrder() throws Exception {
        // given
        insertNewChargeWithId(700L, now().plusHours(1));
        insertNewChargeWithId(800L, now().plusHours(2));
        insertNewChargeWithId(900L, now().plusHours(3));
        insertNewChargeWithId(600L, now().plusHours(4));
        insertNewChargeWithId(500L, now().plusHours(5));
        ChargeSearchParams params = new ChargeSearchParams()
            .withGatewayAccountId(defaultTestAccount.getAccountId());

        // when
        List<TransactionEntity> charges = transactionDao.findAllBy(params);

        // then
        assertThat(charges.size(), is(5));
        assertThat(charges.get(0).getId(), is(500L));
        assertThat(charges.get(1).getId(), is(600L));
        assertThat(charges.get(2).getId(), is(900L));
        assertThat(charges.get(3).getId(), is(800L));
        assertThat(charges.get(4).getId(), is(700L));
    }

    @Test
    public void searchChargesWithSizeAndPageSetshouldGetChargesInCreationDateOrder() throws Exception {
        // given
        insertNewChargeWithId(900L, now().plusHours(1));
        insertNewChargeWithId(800L, now().plusHours(2));
        insertNewChargeWithId(700L, now().plusHours(3));
        insertNewChargeWithId(600L, now().plusHours(4));
        insertNewChargeWithId(500L, now().plusHours(5));
        insertNewChargeWithId(400L, now().plusHours(6));
        insertNewChargeWithId(300L, now().plusHours(7));
        insertNewChargeWithId(200L, now().plusHours(8));

        // when
        ChargeSearchParams params = new ChargeSearchParams()
            .withGatewayAccountId(defaultTestAccount.getAccountId())
            .withPage(1L)
            .withDisplaySize(3L);
        List<TransactionEntity> charges = transactionDao.findAllBy(params);
        // then
        assertThat(charges.size(), is(3));
        assertThat(charges.get(0).getId(), is(200L));
        assertThat(charges.get(1).getId(), is(300L));
        assertThat(charges.get(2).getId(), is(400L));

        // when
        params = new ChargeSearchParams()
            .withGatewayAccountId(defaultTestAccount.getAccountId())
            .withPage(2L)
            .withDisplaySize(3L);
        charges = transactionDao.findAllBy(params);
        // then
        assertThat(charges.size(), is(3));
        assertThat(charges.get(0).getId(), is(500L));
        assertThat(charges.get(1).getId(), is(600L));
        assertThat(charges.get(2).getId(), is(700L));

        // when
        params = new ChargeSearchParams()
            .withGatewayAccountId(defaultTestAccount.getAccountId())
            .withPage(3L)
            .withDisplaySize(3L);
        charges = transactionDao.findAllBy(params);
        // then
        assertThat(charges.size(), is(2));
        assertThat(charges.get(0).getId(), is(800L));
        assertThat(charges.get(1).getId(), is(900L));
    }

    @Test
    public void shouldGetTotalCount_5_when_displaySizeIs_2() {
        // given
        insertNewChargeWithId(700L, now().plusHours(1));
        insertNewChargeWithId(800L, now().plusHours(2));
        insertNewChargeWithId(900L, now().plusHours(3));
        insertNewChargeWithId(600L, now().plusHours(4));
        insertNewChargeWithId(500L, now().plusHours(5));
        ChargeSearchParams params = new ChargeSearchParams()
            .withGatewayAccountId(defaultTestAccount.getAccountId())
            .withExternalChargeState(EXTERNAL_CREATED.getStatus())
            .withDisplaySize(2L);

        // when
        Long count = transactionDao.getTotalFor(params);

        // then gets the count(*) irrespective of the max results (display_size)
        assertThat("total count for transactions mismatch", count, is(5L));
    }

    @Test
    public void searchTransactionsByFullPaymentRequestReferenceOnly() throws Exception {
        // given
        insertTestChargeNew();
        insertTestRefundNew();
        ChargeSearchParams params = new ChargeSearchParams()
            .withGatewayAccountId(defaultTestAccount.getAccountId())
            .withReferenceLike(defaultTestPaymentRequest.getReference());

        // when
        List<TransactionEntity> transactions = transactionDao.findAllBy(params);

        // then
        assertThat(transactions.size(), is(2));

        RefundEntityNew refund = (RefundEntityNew) transactions.get(0);
        ChargeEntityNew charge = (ChargeEntityNew) transactions.get(1);
        assertThat(charge.getId(), is(defaultTestChargeNew.getTransactionId()));
        assertThat(charge.getReference(), is(defaultTestChargeNew.getReference()));
        assertThat(refund.getId(), is(defaultTestRefundNew.getTransactionId()));
    }

    @Test
    public void searchChargesByPartialPaymentRequestReferenceOnly() throws Exception {
        // given
        String paymentReference = "Council Tax Payment bla 2";
        TestPaymentRequest testPaymentRequest = DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestPaymentRequest()
            .withGatewayAccountId(defaultTestAccount.getAccountId())
            .withReference(paymentReference)
            .insert();
        TestChargeNew testCharge = DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestChargeNew()
            .withTestPaymentRequest(testPaymentRequest)
            .insert();

        TestRefundNew testRefund = DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestRefundNew()
            .withTestPaymentRequest(testPaymentRequest)
            .insert();

        String reference = "bla";
        ChargeSearchParams params = new ChargeSearchParams()
            .withGatewayAccountId(defaultTestAccount.getAccountId())
            .withReferenceLike(reference);

        // when
        List<TransactionEntity> transactions = transactionDao.findAllBy(params);

        // then
        assertThat(transactions.size(), is(2));
        RefundEntityNew refund = (RefundEntityNew) transactions.get(0);
        ChargeEntityNew charge = (ChargeEntityNew) transactions.get(1);

        assertThat(charge.getId(), is(testCharge.getTransactionId()));
        assertThat(charge.getReference(), is(testCharge.getReference()));
        assertThat(refund.getId(), is(testRefund.getTransactionId()));
    }

    @Test
    public void searchChargesByPaymentRequestReferenceAndEmail_with_under_score() throws Exception {
        // since '_' have special meaning in like queries of postgres this was resulting in undesired results
        TestPaymentRequest testPaymentRequest = DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestPaymentRequest()
            .withGatewayAccountId(defaultTestAccount.getAccountId())
            .withReference("under_score_ref")
            .insert();
        DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestChargeNew()
            .withTestPaymentRequest(testPaymentRequest)
            .withEmail("under_score@mail.com")
            .insert();

        testPaymentRequest = DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestPaymentRequest()
            .withGatewayAccountId(defaultTestAccount.getAccountId())
            .withReference("understand")
            .insert();

        DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestChargeNew()
            .withTestPaymentRequest(testPaymentRequest)
            .withEmail("undertaker@mail.com")
            .insert();

        ChargeSearchParams params = new ChargeSearchParams()
            .withGatewayAccountId(defaultTestAccount.getAccountId())
            .withReferenceLike("under_")
            .withEmailLike("under_");

        // when
        List<ChargeEntityNew> charges = transactionDao.findAllBy(params);

        // then
        assertThat(charges.size(), is(1));

        ChargeEntityNew charge = charges.get(0);
        assertThat(charge.getReference(), is("under_score_ref"));
        assertThat(charge.getEmail(), is("under_score@mail.com"));
    }

    @Test
    public void searchChargesByPaymentRequestReferenceWithPercentSign() throws Exception {
        // since '%' have special meaning in like queries of postgres this was resulting in undesired results
        TestPaymentRequest testPaymentRequest = DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestPaymentRequest()
            .withGatewayAccountId(defaultTestAccount.getAccountId())
            .withReference("percentref")
            .insert();
        DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestChargeNew()
            .withTestPaymentRequest(testPaymentRequest)
            .insert();

        testPaymentRequest = DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestPaymentRequest()
            .withGatewayAccountId(defaultTestAccount.getAccountId())
            .withReference("percent%ref")
            .insert();

        DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestChargeNew()
            .withTestPaymentRequest(testPaymentRequest)
            .insert();

        ChargeSearchParams params = new ChargeSearchParams()
            .withGatewayAccountId(defaultTestAccount.getAccountId())
            .withReferenceLike("percent%");

        // when
        List<ChargeEntityNew> charges = transactionDao.findAllBy(params);

        // then
        assertThat(charges.size(), is(1));

        ChargeEntityNew charge = charges.get(0);
        assertThat(charge.getReference(), is("percent%ref"));
    }

    @Test
    public void searchChargesByPaymentRequestReferenceAndEmailShouldBeCaseInsensitive() throws Exception {
        // fix that the reference and email searches should be case insensitive
        TestPaymentRequest testPaymentRequest = DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestPaymentRequest()
            .withGatewayAccountId(defaultTestAccount.getAccountId())
            .withReference("case-Insensitive-ref")
            .insert();
        DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestChargeNew()
            .withTestPaymentRequest(testPaymentRequest)
            .withEmail("email-id@mail.com")
            .insert();

        testPaymentRequest = DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestPaymentRequest()
            .withGatewayAccountId(defaultTestAccount.getAccountId())
            .withReference("Case-inSENSITIVE-Ref")
            .insert();
        DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestChargeNew()
            .withTestPaymentRequest(testPaymentRequest)
            .withEmail("EMAIL-ID@MAIL.COM")
            .insert();

        ChargeSearchParams params = new ChargeSearchParams()
            .withGatewayAccountId(defaultTestAccount.getAccountId())
            .withReferenceLike("cASe-insEnsiTIve")
            .withEmailLike("EMAIL-ID@mail.com");

        // when
        List<ChargeEntityNew> charges = transactionDao.findAllBy(params);

        // then
        assertThat(charges.size(), is(2));

        ChargeEntityNew charge = charges.get(0);
        assertThat(charge.getReference(), is("Case-inSENSITIVE-Ref"));
        assertThat(charge.getEmail(), is("EMAIL-ID@MAIL.COM"));

        charge = charges.get(1);
        assertThat(charge.getReference(), is("case-Insensitive-ref"));
        assertThat(charge.getEmail(), is("email-id@mail.com"));
    }

    @Test
    public void aBasicTestAgainstSqlInjection() throws Exception {
        // given
        TestPaymentRequest testPaymentRequest = DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestPaymentRequest()
            .withGatewayAccountId(defaultTestAccount.getAccountId())
            .withReference("reference")
            .insert();
        DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestChargeNew()
            .withTestPaymentRequest(testPaymentRequest)
            .insert();

        ChargeSearchParams params = new ChargeSearchParams()
            .withReferenceLike("reference");
        // when passed in a simple reference string
        List<TransactionEntity> charges = transactionDao.findAllBy(params);
        // then it fetches a single result
        assertThat(charges.size(), is(1));

        // when passed in a non existent reference with an sql injected string
        String sqlInjectionReferenceString = "reffff%' or 1=1 or c.reference like '%1";
        params = new ChargeSearchParams()
            .withReferenceLike(sqlInjectionReferenceString);
        charges = transactionDao.findAllBy(params);
        // then it fetches no result
        // with a typical sql injection vulnerable query doing this should fetch all results
        assertThat(charges.size(), is(0));
    }


    @Test
    public void searchChargeByPaymentRequestReferenceAndLegacyStatusOnly() throws Exception {
        // given
        TestPaymentRequest te = DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestPaymentRequest()
            .withGatewayAccountId(defaultTestAccount.getAccountId())
            .withDescription("Test description")
            .insert();
        TestChargeNew testChargeNew = DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestChargeNew()
            .withTestPaymentRequest(te)
            .insert();
        ChargeSearchParams params = new ChargeSearchParams()
            .withGatewayAccountId(defaultTestAccount.getAccountId())
            .withReferenceLike(te.getReference())
            .withExternalChargeState(EXTERNAL_CREATED.getStatus());
        defaultTestCardDetails
            .withChargeId(testChargeNew.transactionId)
            .update();
        // when
        List<ChargeEntityNew> charges = transactionDao.findAllBy(params);

        // then
        assertThat(charges.size(), is(1));
        ChargeEntityNew charge = charges.get(0);

        assertThat(charge.getId(), is(testChargeNew.getTransactionId()));
        assertThat(charge.getAmount(), is(testChargeNew.getAmount()));
        assertThat(charge.getReference(), is(te.getReference()));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getStatus(), is(testChargeNew.getTransactionStatus().toString()));
        assertThat(charge.getCardDetails().getCardBrand(), is(defaultTestCardDetails.getCardBrand()));
        assertDateMatch(charge.getCreatedDate().toString());
    }

    @Test
    public void searchChargeByPaymentRequestReferenceAndStatusAndFromDateAndToDate() throws Exception {

        // given
        insertTestChargeNew();
        ChargeSearchParams params = new ChargeSearchParams()
            .withGatewayAccountId(defaultTestAccount.getAccountId())
            .withReferenceLike(defaultTestPaymentRequest.getReference())
            .withExternalChargeState(EXTERNAL_CREATED.getStatus())
            .withFromDate(ZonedDateTime.parse(FROM_DATE))
            .withToDate(ZonedDateTime.parse(TO_DATE));

        // when
        List<ChargeEntityNew> charges = transactionDao.findAllBy(params);

        // then
        assertThat(charges.size(), is(1));
        ChargeEntityNew charge = charges.get(0);

        assertThat(charge.getId(), is(defaultTestChargeNew.getTransactionId()));
        assertThat(charge.getAmount(), is(defaultTestChargeNew.getAmount()));
        assertThat(charge.getReference(), is(defaultTestPaymentRequest.getReference()));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getStatus(), is(defaultTestChargeNew.getTransactionStatus().toString()));
        assertThat(charge.getCardDetails().getCardBrand(), is(defaultTestCardDetails.getCardBrand()));
        assertDateMatch(charge.getCreatedDate().toString());
    }

    @Test
    public void searchChargeByReferenceAndStatusAndFromDate() throws Exception {

        // given
        insertTestChargeNew();
        ChargeSearchParams params = new ChargeSearchParams()
            .withGatewayAccountId(defaultTestAccount.getAccountId())
            .withReferenceLike(defaultTestPaymentRequest.getReference())
            .withExternalChargeState(EXTERNAL_CREATED.getStatus())
            .withFromDate(ZonedDateTime.parse(FROM_DATE));

        // when
        List<ChargeEntityNew> charges = transactionDao.findAllBy(params);

        // then
        assertThat(charges.size(), is(1));
        ChargeEntityNew charge = charges.get(0);

        assertThat(charge.getId(), is(defaultTestChargeNew.getTransactionId()));
        assertThat(charge.getAmount(), is(defaultTestChargeNew.getAmount()));
        assertThat(charge.getReference(), is(defaultTestPaymentRequest.getReference()));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getStatus(), is(defaultTestChargeNew.getTransactionStatus().toString()));
        assertThat(charge.getCardDetails().getCardBrand(), is(defaultTestCardDetails.getCardBrand()));

        assertDateMatch(charge.getCreatedDate().toString());
    }

    @Test
    public void searchChargeByMultipleStatuses() {

        // given
        insertTestChargeNew();
        DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestChargeNew()
            .withTransactionId(12345)
            .withTestPaymentRequest(defaultTestPaymentRequest)
            .withTransactionStatus(ENTERING_CARD_DETAILS)
            .insert();

        DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestChargeNew()
            .withTransactionId(12346)
            .withTestPaymentRequest(defaultTestPaymentRequest)
            .withTransactionStatus(AUTHORISATION_READY)
            .insert();

        ChargeSearchParams params = new ChargeSearchParams()
            .withGatewayAccountId(defaultTestAccount.getAccountId())
            .withReferenceLike(defaultTestPaymentRequest.getReference())
            .withExternalChargeState(EXTERNAL_STARTED.getStatus())
            .withFromDate(ZonedDateTime.parse(FROM_DATE));

        // when
        List<TransactionEntity> charges = transactionDao.findAllBy(params);

        // then
        assertThat(charges.size(), is(2));
        assertThat(charges.get(0).getStatus(), is(AUTHORISATION_READY.getValue()));
        assertThat(charges.get(1).getStatus(), is(ENTERING_CARD_DETAILS.getValue()));
    }

    @Test
    public void searchChargesShouldBeOrderedByCreationDateDescending() {
        // given
        this.defaultTestPaymentRequest = DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestPaymentRequest()
            .withGatewayAccountId(defaultTestAccount.getAccountId())
            .insert();

        DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestChargeNew()
            .withTransactionId(555L)
            .withTestPaymentRequest(defaultTestPaymentRequest)
            .insert();

        DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestChargeNew()
            .withTransactionId(557L)
            .withTestPaymentRequest(defaultTestPaymentRequest)
            .insert();

        DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestChargeNew()
            .withTransactionId(556L)
            .withTestPaymentRequest(defaultTestPaymentRequest)
            .insert();

        ChargeSearchParams params = new ChargeSearchParams()
            .withGatewayAccountId(defaultTestAccount.getAccountId());

        // when
        List<TransactionEntity> charges = transactionDao.findAllBy(params);

        // then
        assertThat(charges.size(), is(3));
        assertThat(charges.get(0).getId(), is(556L));
        assertThat(charges.get(1).getId(), is(557L));
        assertThat(charges.get(2).getId(), is(555L));
    }

    @Test
    public void searchChargeByReferenceAndStatusAndEmailAndCardBrandAndFromDateAndToDate() throws Exception {
        // given
        insertTestChargeNew();
        ChargeSearchParams params = new ChargeSearchParams()
            .withGatewayAccountId(defaultTestAccount.getAccountId())
            .withReferenceLike(defaultTestPaymentRequest.getReference())
            .withExternalChargeState(EXTERNAL_CREATED.getStatus())
            .withCardBrand(defaultTestCardDetails.getCardBrand())
            .withEmailLike(defaultTestChargeNew.getEmail())
            .withFromDate(ZonedDateTime.parse(FROM_DATE))
            .withToDate(ZonedDateTime.parse(TO_DATE));

        // when
        List<ChargeEntityNew> charges = transactionDao.findAllBy(params);

        // then
        assertThat(charges.size(), is(1));
        ChargeEntityNew charge = charges.get(0);

        assertThat(charge.getId(), is(defaultTestChargeNew.getTransactionId()));
        assertThat(charge.getAmount(), is(defaultTestChargeNew.getAmount()));
        assertThat(charge.getReference(), is(defaultTestPaymentRequest.getReference()));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getStatus(), is(defaultTestChargeNew.getTransactionStatus().toString()));
        assertThat(charge.getCardDetails().getCardBrand(), is(defaultTestCardDetails.getCardBrand()));
        assertDateMatch(charge.getCreatedDate().toString());
    }

    @Test
    public void searchChargeByReferenceAndStatusAndToDate() throws Exception {

        // given
        insertTestChargeNew();
        ChargeSearchParams params = new ChargeSearchParams()
            .withGatewayAccountId(defaultTestAccount.getAccountId())
            .withReferenceLike(defaultTestPaymentRequest.getReference())
            .withExternalChargeState(EXTERNAL_CREATED.getStatus())
            .withToDate(ZonedDateTime.parse(TO_DATE));

        // when
        List<ChargeEntityNew> charges = transactionDao.findAllBy(params);

        // then
        assertThat(charges.size(), is(1));
        ChargeEntityNew charge = charges.get(0);

        assertThat(charge.getId(), is(defaultTestChargeNew.getTransactionId()));
        assertThat(charge.getAmount(), is(defaultTestChargeNew.getAmount()));
        assertThat(charge.getReference(), is(defaultTestPaymentRequest.getReference()));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getStatus(), is(defaultTestChargeNew.getTransactionStatus().toString()));
        assertThat(charge.getCardDetails().getCardBrand(), is(defaultTestCardDetails.getCardBrand()));
        assertDateMatch(charge.getCreatedDate().toString());
    }

    @Test
    public void searchChargeByReferenceAndStatusAndFromDate_ShouldReturnZeroIfDateIsNotInRange() throws Exception {
        insertTestChargeNew();

        ChargeSearchParams params = new ChargeSearchParams()
            .withGatewayAccountId(defaultTestAccount.getAccountId())
            .withReferenceLike(defaultTestPaymentRequest.getReference())
            .withExternalChargeState(EXTERNAL_CREATED.getStatus())
            .withFromDate(ZonedDateTime.parse(TO_DATE));

        // when
        List<TransactionEntity> charges = transactionDao.findAllBy(params);

        assertThat(charges.size(), is(0));
    }

    @Test
    public void searchChargeByReferenceAndStatusAndToDate_ShouldReturnZeroIfToDateIsNotInRange() throws Exception {
        insertTestChargeNew();
        ChargeSearchParams params = new ChargeSearchParams()
            .withGatewayAccountId(defaultTestAccount.getAccountId())
            .withReferenceLike(defaultTestPaymentRequest.getReference())
            .withExternalChargeState(EXTERNAL_CREATED.getStatus())
            .withToDate(ZonedDateTime.parse(FROM_DATE));

        List<TransactionEntity> charges = transactionDao.findAllBy(params);

        assertThat(charges.size(), is(0));
    }

    private Matcher<? super List<TransactionEventEntity>> shouldIncludeStatus(TransactionStatus... expectedStatuses) {
        return new TypeSafeMatcher<List<TransactionEventEntity>>() {
            @Override
            protected boolean matchesSafely(List<TransactionEventEntity> chargeEvents) {
                List<TransactionStatus> actualStatuses = chargeEvents.stream()
                    .map(TransactionEventEntity::getStatus)
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

    @Test
    public void shouldUpdateEventsWhenMergeWithChargeEntityWithNewStatus() {

        Long chargeId = 56735L;
        String externalChargeId = "charge456";

        String transactionId = "345654";
        String transactionId2 = "345655";
        String transactionId3 = "345656";

        TestPaymentRequest testPaymentRequest = DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestPaymentRequest()
            .withGatewayAccountId(defaultTestAccount.getAccountId())
            .withExternalId(externalChargeId)
            .insert();
        DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestChargeNew()
            .withTestPaymentRequest(testPaymentRequest)
            .withTransactionId(chargeId)
            .insert();

        Optional<ChargeEntityNew> charge = transactionDao.findById(chargeId);
        TransactionEntity entity = charge.get();
        entity.setStatus(ENTERING_CARD_DETAILS);

        entity = transactionDao.mergeAndNotifyStatusHasChanged(entity, Optional.empty());

        //move status to AUTHORISED 
        entity.setStatus(AUTHORISATION_READY);
        entity.setStatus(AUTHORISATION_SUCCESS);
        ((ChargeEntityNew)entity).setGatewayTransactionId(transactionId2);
        entity = transactionDao.mergeAndNotifyStatusHasChanged(entity, Optional.empty());

        entity.setStatus(CAPTURE_READY);
        ((ChargeEntityNew)entity).setGatewayTransactionId(transactionId3);
        transactionDao.mergeAndNotifyStatusHasChanged(entity, Optional.empty());

        List<TransactionEventEntity> events = ((TransactionEntity)transactionDao.findById(chargeId).get()).getEvents();

        assertThat(events, hasSize(3));
        assertThat(events, shouldIncludeStatus(ENTERING_CARD_DETAILS));
        assertThat(events, shouldIncludeStatus(AUTHORISATION_SUCCESS));
        assertThat(events, shouldIncludeStatus(CAPTURE_READY));
        assertDateMatch(events.get(0).getUpdated());
    }

    @Test
    public void chargeEvents_shouldRecordTransactionIdWithEachStatusChange() throws Exception {
        Long chargeId = 56735L;
        String externalChargeId = "charge456";

        String gatewayTransactionId = "345654";

        TestPaymentRequest testPaymentRequest = DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestPaymentRequest()
            .withGatewayAccountId(defaultTestAccount.getAccountId())
            .withExternalId(externalChargeId)
            .insert();


        DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestChargeNew()
            .withTestPaymentRequest(testPaymentRequest)
            .withTransactionId(chargeId)
            .withGatewayTransactionId(gatewayTransactionId)
            .insert();

        Optional<ChargeEntityNew> charge = transactionDao.findById(chargeId);
        ChargeEntityNew entity = charge.get();
        entity.setStatus(ENTERING_CARD_DETAILS);
    }

    @Test
    public void invalidSizeOfReference() throws Exception {
        expectedEx.expect(RuntimeException.class);

        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity(defaultTestAccount.getPaymentProvider(), new HashMap<>(), TEST);
        gatewayAccount.setId(defaultTestAccount.getAccountId());
        PaymentRequestEntity paymentRequestEntity = PaymentRequestEntityFixture.aValidPaymentRequestEntity().withReference(RandomStringUtils.randomAlphanumeric(256)).build();
        TransactionEntity transactionEntity = TransactionEntityFixture.aValidChargeEntity().withPaymentRequestEntity(paymentRequestEntity).build();
        transactionDao.persist(transactionEntity);
    }

    @Test
    public void shouldCreateANewCharge() {

        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity(defaultTestAccount.getPaymentProvider(), new HashMap<>(), TEST);
        gatewayAccount.setId(defaultTestAccount.getAccountId());

        PaymentRequestEntity paymentRequestEntity = PaymentRequestEntityFixture.aValidPaymentRequestEntity().withGatewayAccount(gatewayAccount).build();
        TransactionEntity chargeEntity = aValidChargeEntity()
            .withId(null)
            .withPaymentRequestEntity(paymentRequestEntity)
            .build();

        assertThat(chargeEntity.getId(), is(nullValue()));

        transactionDao.persist(chargeEntity);

        assertThat(chargeEntity.getId(), is(notNullValue()));
//         Ensure always max precision is being millis
        assertThat(chargeEntity.getCreatedDate().getNano() % 1000000, is(0));
    }

    @Test
    public void shouldCreateANewChargeWith3dsDetails() {
        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity(defaultTestAccount.getPaymentProvider(), new HashMap<>(), TEST);
        gatewayAccount.setId(defaultTestAccount.getAccountId());

        PaymentRequestEntity paymentRequestEntity = PaymentRequestEntityFixture.aValidPaymentRequestEntity().withGatewayAccount(gatewayAccount).build();

        String paRequest = "3dsPaRequest";
        String issuerUrl = "https://issuer.example.com/3ds";
        Auth3dsDetailsEntity auth3dsDetailsEntity = new Auth3dsDetailsEntity(paRequest,
            issuerUrl, null);
        TransactionEntity chargeEntity = aValidChargeEntity()
            .withPaymentRequestEntity(paymentRequestEntity)
            .withAuth3dsDetailsEntity(auth3dsDetailsEntity)
            .build();

        assertThat(chargeEntity.getId(), is(nullValue()));
        transactionDao.persist(chargeEntity);
        Optional<ChargeEntityNew> charge = transactionDao.findById(chargeEntity.getId());
        assertThat(charge.get().getAuth3dsDetails().getPaRequest(), is(paRequest));
        assertThat(charge.get().getAuth3dsDetails().getIssuerUrl(), is(issuerUrl));
        assertThat(charge.get().getAuth3dsDetails().getWorldpayCookieValue(), is(nullValue()));
    }

    @Test
    public void shouldCreateANewChargeWithWorldpayCookie() {
        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity(defaultTestAccount.getPaymentProvider(), new HashMap<>(), TEST);
        gatewayAccount.setId(defaultTestAccount.getAccountId());

        PaymentRequestEntity paymentRequestEntity = PaymentRequestEntityFixture.aValidPaymentRequestEntity().withGatewayAccount(gatewayAccount).build();
        String paRequest = "3dsPaRequest";
        String issuerUrl = "https://issuer.example.com/3ds";
        String worldpayCookie = "worldpay_cookie";

        Auth3dsDetailsEntity auth3dsDetailsEntity = new Auth3dsDetailsEntity(paRequest,
            issuerUrl, worldpayCookie);
        TransactionEntity chargeEntity = aValidChargeEntity()
            .withPaymentRequestEntity(paymentRequestEntity)
            .withAuth3dsDetailsEntity(auth3dsDetailsEntity)
            .build();

        assertThat(chargeEntity.getId(), is(nullValue()));

        transactionDao.persist(chargeEntity);

        Optional<TransactionEntity> charge = transactionDao.findById(chargeEntity.getId());
        assertThat(charge.get().getAuth3dsDetails().getPaRequest(), is(paRequest));
        assertThat(charge.get().getAuth3dsDetails().getIssuerUrl(), is(issuerUrl));
        assertThat(charge.get().getAuth3dsDetails().getWorldpayCookieValue(), is(worldpayCookie));
    }

    @Test
    public void shouldReturnNullFindingByIdWhenChargeDoesNotExist() {

        Optional<TransactionEntity> charge = transactionDao.findById(5686541L);

        assertThat(charge.isPresent(), is(false));
    }

    @Test
    public void shouldFindChargeEntityByProviderAndTransactionId() {

        // given
        String transactionId = "7826782163";
        ZonedDateTime createdDate = now(ZoneId.of("UTC"));
        Long chargeId = 9999L;
        String externalChargeId = "charge9999";

        TestPaymentRequest testPaymentRequest = DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestPaymentRequest()
            .withGatewayAccountId(defaultTestAccount.getAccountId())
            .withExternalId(externalChargeId)
            .insert();

        DatabaseFixtures.TestChargeNew testCharge = DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestChargeNew()
            .withTestPaymentRequest(testPaymentRequest)
            .withTransactionId(chargeId)
            .withCreatedDate(createdDate)
            .withGatewayTransactionId(transactionId)
            .insert();

        DatabaseFixtures.TestCardDetails testCardDetails = DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper)
            .validTestCardDetails()
            .withChargeId(chargeId)
            .update();

        // when
        Optional<ChargeEntityNew> chargeOptional = transactionDao.findByProviderAndTransactionId(defaultTestAccount.getPaymentProvider(), transactionId);

        // then
        assertThat(chargeOptional.isPresent(), is(true));

        ChargeEntityNew charge = chargeOptional.get();
        assertThat(charge.getId(), is(chargeId));
        assertThat(charge.getGatewayTransactionId(), is(transactionId));
        assertThat(charge.getReturnUrl(), is(testCharge.getReturnUrl()));
        assertThat(charge.getStatus(), is(CREATED.getValue()));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getCreatedDate(), is(createdDate));
        assertThat(charge.getReference(), is(testCharge.getReference()));
        assertThat(charge.getGatewayAccount(), is(notNullValue()));
        assertThat(charge.getCardDetails().getCardBrand(), is(testCardDetails.getCardBrand()));
    }

    @Test
    public void shouldGetGatewayAccountWhenFindingChargeEntityByProviderAndTransactionId() {

        String transactionId = "7826782163";

        TestPaymentRequest testPaymentRequest = DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestPaymentRequest()
            .withGatewayAccountId(defaultTestAccount.getAccountId())
            .withExternalId("charge8888")
            .insert();

        DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestChargeNew()
            .withTransactionId(8888L)
            .withTestPaymentRequest(testPaymentRequest)
            .withGatewayTransactionId(transactionId)
            .insert();

        Optional<ChargeEntityNew> chargeOptional = transactionDao.findByProviderAndTransactionId(defaultTestAccount.getPaymentProvider(), transactionId);

        assertThat(chargeOptional.isPresent(), is(true));

        ChargeEntityNew charge = chargeOptional.get();
        GatewayAccountEntity gatewayAccount = charge.getGatewayAccount();
        assertThat(gatewayAccount, is(notNullValue()));
        assertThat(gatewayAccount.getId(), is(defaultTestAccount.getAccountId()));
        assertThat(gatewayAccount.getGatewayName(), is(defaultTestAccount.getPaymentProvider()));
        assertThat(gatewayAccount.getCredentials(), is(Collections.EMPTY_MAP));
    }

    @Test
    public void shouldGetChargeByChargeIdWithCorrectAssociatedAccountId() {
        String transactionId = "7826782163";
        ZonedDateTime createdDate = now(ZoneId.of("UTC"));
        Long chargeId = 876786L;
        String externalChargeId = "charge876786";

        TestPaymentRequest testPaymentRequest = DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestPaymentRequest()
            .withGatewayAccountId(defaultTestAccount.getAccountId())
            .withExternalId(externalChargeId)
            .insert();

        TestChargeNew testCharge = DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestChargeNew()
            .withTransactionId(chargeId)
            .withTestPaymentRequest(testPaymentRequest)
            .withGatewayTransactionId(transactionId)
            .withCreatedDate(createdDate)
            .insert();

        DatabaseFixtures.TestCardDetails testCardDetails = DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper)
            .validTestCardDetails()
            .withChargeId(chargeId)
            .update();
        Optional<ChargeEntityNew> chargeMaybe = transactionDao.findByExternalIdAndGatewayAccount(externalChargeId, defaultTestAccount.getAccountId());

        assertThat(chargeMaybe.isPresent(), is(true));
        ChargeEntityNew charge = chargeMaybe.get();

        assertThat(charge.getId(), is(chargeId));
        assertThat(charge.getGatewayTransactionId(), is(transactionId));
        assertThat(charge.getReturnUrl(), is(testCharge.getReturnUrl()));
        assertThat(charge.getStatus(), is(testCharge.getTransactionStatus().toString()));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getCreatedDate(), is(createdDate));
        assertThat(charge.getReference(), is(testCharge.getReference()));
        assertThat(charge.getGatewayAccount(), is(notNullValue()));
        assertThat(charge.getGatewayAccount().getId(), is(defaultTestAccount.getAccountId()));
        assertThat(charge.getCardDetails().getCardBrand(), is(testCardDetails.getCardBrand()));
    }

    @Test
    public void shouldGetChargeByChargeIdAsNullWhenAccountIdDoesNotMatch() {
        insertTestChargeNew();
        Optional<TransactionEntity> chargeForAccount = transactionDao.findByExternalIdAndGatewayAccount(defaultTestPaymentRequest.getExternalId(), 456781L);
        assertThat(chargeForAccount.isPresent(), is(false));
    }

    @Test
    public void findById_shouldFindChargesAndRefundsForAPaymentRequest() throws Exception {
        // given
        TestPaymentRequest testPaymentRequest = DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestPaymentRequest()
            .withGatewayAccountId(defaultTestAccount.getAccountId())
            .insert();

        TestChargeNew testCharge = DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestChargeNew()
            .withTestPaymentRequest(testPaymentRequest)
            .insert();
        defaultTestCardDetails
            .withChargeId(testCharge.transactionId)
            .update();
        TestRefundNew testRefund = DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestRefundNew()
            .withTestPaymentRequest(testPaymentRequest)
            .insert();

        // when
        List<TransactionEntity> transactions = transactionDao.findAllByPaymentRequestId(testPaymentRequest.id);

        assertThat(transactions.size(), is(2));
        // then
        ChargeEntityNew charge = (ChargeEntityNew) transactions.get(0);
        RefundEntityNew refund = (RefundEntityNew) transactions.get(1);
        assertThat(charge.getId(), is(testCharge.getTransactionId()));
        assertThat(charge.getAmount(), is(testCharge.getAmount()));
        assertThat(charge.getReference(), is(testCharge.getReference()));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getStatus(), is(CREATED.getValue()));
        assertThat(charge.getGatewayAccount().getId(), is(defaultTestAccount.getAccountId()));
        assertThat(charge.getReturnUrl(), is(testCharge.getReturnUrl()));
        assertThat(charge.getCreatedDate(), is(testCharge.getCreatedDate()));
        assertThat(charge.getCardDetails().getCardBrand(), is(defaultTestCardDetails.getCardBrand()));
        assertNotNull(charge.getVersion());

        assertThat(refund.getId(), is(testRefund.getTransactionId()));
        assertThat(refund.getAmount(), is(testRefund.getAmount()));
        assertThat(refund.getStatus(), is(testRefund.getTransactionStatus().toString()));
        assertThat(refund.getCreatedDate(), is(testRefund.getCreatedDate()));
        assertNotNull(refund.getVersion());
    }

    @Test
    public void findByRefundExternalId_shouldFindARefundEntity() {
        insertTestRefundNew();
        Optional<TransactionEntity> refundMaybe = transactionDao.findRefundByExternalId(defaultTestRefundNew.getExternalId());
        assertThat(refundMaybe.isPresent(), is(true));
    }

    @Test
    public void findByRefundExternalId_shouldNotFindARefundEntity() {
        Optional<TransactionEntity> refundMaybe = transactionDao.findRefundByExternalId("abcdefg123");
        assertThat(refundMaybe.isPresent(), is(false));
    }

    @Test
    public void testFindByDate_status_findsValidChargeForStatus() throws Exception {
        this.defaultTestPaymentRequest = DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestPaymentRequest()
            .withGatewayAccountId(defaultTestAccount.getAccountId())
            .insert();
        DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestChargeNew()
            .withTestPaymentRequest(defaultTestPaymentRequest)
            .withTransactionId(100L)
            .withCreatedDate(now().minusHours(2))
            .insert();

        ArrayList<TransactionStatus> chargeStatuses = Lists.newArrayList(CREATED, ENTERING_CARD_DETAILS, AUTHORISATION_SUCCESS);

        List<TransactionEntity> charges = transactionDao.findBeforeDateWithStatusIn(now().minusHours(1), chargeStatuses);

        assertThat(charges.size(), is(1));
        assertEquals(charges.get(0).getId(), new Long(100));
    }

    @Test
    public void testFindByDateStatus_findsNoneForValidStatus() throws Exception {
        this.defaultTestPaymentRequest = DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestPaymentRequest()
            .withGatewayAccountId(defaultTestAccount.getAccountId())
            .insert();

        DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestChargeNew()
            .withTestPaymentRequest(defaultTestPaymentRequest)
            .withTransactionId(100L)
            .withCreatedDate(now().minusHours(2))
            .insert();

        ArrayList<TransactionStatus> chargeStatuses = Lists.newArrayList(CAPTURE_READY, SYSTEM_CANCELLED);

        List<TransactionEntity> charges = transactionDao.findBeforeDateWithStatusIn(now().minusHours(1), chargeStatuses);

        assertThat(charges.size(), is(0));
    }

    @Test
    public void testFindByDateStatus_findsNoneForExpiredDate() throws Exception {
        this.defaultTestPaymentRequest = DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestPaymentRequest()
            .withGatewayAccountId(defaultTestAccount.getAccountId())
            .insert();

        DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestChargeNew()
            .withTestPaymentRequest(defaultTestPaymentRequest)
            .withTransactionId(100L)
            .withCreatedDate(now().minusMinutes(30))
            .insert();

        ArrayList<TransactionStatus> chargeStatuses = Lists.newArrayList(CREATED, ENTERING_CARD_DETAILS, AUTHORISATION_SUCCESS);

        List<TransactionEntity> charges = transactionDao.findBeforeDateWithStatusIn(now().minusHours(1), chargeStatuses);

        assertThat(charges.size(), is(0));
    }

    @Test
    public void findById_shouldFindRefund() {
        insertTestRefundNew();
        Optional<RefundEntityNew> refundEntityOptional = transactionDao.findById(RefundEntityNew.class, defaultTestRefundNew.getTransactionId());

        assertThat(refundEntityOptional.isPresent(), is(true));

        RefundEntityNew refundEntity = refundEntityOptional.get();

        assertNotNull(refundEntity.getId());
        assertThat(refundEntity.getAmount(), is(defaultTestRefundNew.getAmount()));
        assertThat(refundEntity.getStatus(), is(defaultTestRefundNew.getTransactionStatus().toString()));
        assertNotNull(refundEntity.getPaymentRequest());
        assertThat(refundEntity.getPaymentRequest().getId(), is(defaultTestRefundNew.getPaymentRequest().getId()));
        assertThat(refundEntity.getCreatedDate(), is(defaultTestRefundNew.getCreatedDate()));
        assertNotNull(refundEntity.getVersion());
    }

    @Test
    public void findByProviderAndReference_shouldFindRefundForSandbox() {
        insertTestRefundNew();
        RefundEntityNew refundEntity = checkRefund("sandbox", defaultTestRefundNew.getExternalId());
        assertThat(refundEntity.getExternalId(), Is.is(defaultTestRefundNew.getExternalId()));
    }

    @Test
    public void findByProviderAndReference_shouldFindRefundForWorldpay() {
        insertTestRefundNew();
        RefundEntityNew refundEntity = checkRefund("worldpay", defaultTestRefundNew.getExternalId());
        assertThat(refundEntity.getExternalId(), Is.is(defaultTestRefundNew.getExternalId()));
    }

    @Test
    public void findByProviderAndReference_shouldFindRefundForSmartpay() {
        insertTestRefundNew();
        RefundEntityNew refundEntity = checkRefund("smartpay", defaultTestRefundNew.getSmartpayPspReference());
        assertThat(refundEntity.getSmartpayPspReference(), Is.is(defaultTestRefundNew.getSmartpayPspReference()));
    }

    @Test
    public void findByProviderAndReference_shouldFindRefundForEpdq() {
        insertTestRefundNew();
        String reference = defaultTestRefundNew.getEpdqPayId() + "/" + defaultTestRefundNew.getEpdqPayIdSub();
        RefundEntityNew refundEntity = checkRefund("epdq", reference);
        assertThat(refundEntity.getExternalId(), Is.is(defaultTestRefundNew.getExternalId()));
    }

    private RefundEntityNew checkRefund(String provider, String reference) {
        Optional<RefundEntityNew> refundEntityOptional = transactionDao.findByProviderAndReference(provider, reference);

        assertThat(refundEntityOptional.isPresent(), Is.is(true));

        RefundEntityNew refundEntity = refundEntityOptional.get();

        assertNotNull(refundEntity.getId());
        assertThat(refundEntity.getAmount(), Is.is(defaultTestRefundNew.getAmount()));
        assertThat(refundEntity.getStatus(), Is.is(defaultTestRefundNew.getTransactionStatus().toString()));
        assertNotNull(refundEntity.getPaymentRequest());
        assertThat(refundEntity.getPaymentRequest().getId(), Is.is(defaultTestRefundNew.getPaymentRequest().getId()));
        assertThat(refundEntity.getCreatedDate(), Is.is(defaultTestRefundNew.getCreatedDate()));
        assertNotNull(refundEntity.getVersion());
        return refundEntity;
    }

    @Test
    public void persist_shouldCreateARefund() {
        GatewayAccountEntity gatewayAccountEntity = new GatewayAccountEntity("smartpay", null, Type.TEST);
        PaymentRequestEntity paymentRequestEntity = PaymentRequestEntityFixture
            .aValidPaymentRequestEntity()
            .withGatewayAccount(gatewayAccountEntity)
            .build();

        paymentRequestDao.persist(paymentRequestEntity);
        RefundEntityNew refundEntity = new RefundEntityNew(
            paymentRequestEntity,
            5L,
            TransactionStatus.REFUND_CREATED,
            ZonedDateTime.now(),
            "smartpayReference",
            null,
            null,
            "me");
        transactionDao.persist(refundEntity);

        assertNotNull(refundEntity.getId());

        List<Map<String, Object>> refundByIdFound = databaseTestHelper.getRefundNew(refundEntity.getId());

        assertThat(refundByIdFound.size(), Is.is(1));
        assertThat(refundByIdFound.get(0), hasEntry("amount", 5L));
        assertThat(refundByIdFound.get(0), hasEntry("status", "REFUND CREATED"));
        assertThat(refundByIdFound.get(0), hasEntry("operation", "REFUND"));
        assertThat(refundByIdFound.get(0), hasEntry("payment_request_id", paymentRequestEntity.getId()));
        assertNotNull(refundByIdFound.get(0).get("refund_external_id"));
        assertThat(refundByIdFound.get(0), hasEntry("refund_smartpay_pspreference", "smartpayReference"));
        assertNull(refundByIdFound.get(0).get("refund_epdq_payid"));
        assertNull(refundByIdFound.get(0).get("refund_epdq_payidsub"));
        assertThat(refundByIdFound.get(0), hasEntry("created_date", java.sql.Timestamp.from(refundEntity.getCreatedDate().toInstant())));
        assertThat(refundByIdFound.get(0), hasEntry("refunded_by", "me"));
    }


    private void insertTestAccount() {
        this.defaultTestAccount = DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestAccount()
            .insert();
    }

    private void insertTestChargeNew() {
        this.defaultTestPaymentRequest = DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestPaymentRequest()
            .withGatewayAccountId(defaultTestAccount.getAccountId())
            .insert();
        this.defaultTestChargeNew = DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestChargeNew()
            .withTestPaymentRequest(defaultTestPaymentRequest)
            .insert();
        defaultTestCardDetails
            .withChargeId(defaultTestChargeNew.transactionId)
            .update();
    }
    private void insertTestCharge() {
        this.defaultTestCharge = DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestCharge()
            .withTestAccount(defaultTestAccount)
            .insert();
        defaultTestCardDetails
            .withChargeId(defaultTestCharge.chargeId)
            .update();
    }
    private void insertTestRefundNew() {
        this.defaultTestPaymentRequest = DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestPaymentRequest()
            .withGatewayAccountId(defaultTestAccount.getAccountId())
            .insert();
        this.defaultTestRefundNew = DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestRefundNew()
            .withTestPaymentRequest(defaultTestPaymentRequest)
            .insert();
    }

    private DatabaseFixtures.TestChargeNew insertNewChargeWithId(Long chargeId, ZonedDateTime creationDate) {
        TestPaymentRequest insert = DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestPaymentRequest()
            .withGatewayAccountId(defaultTestAccount.getAccountId())
            .insert();
        return DatabaseFixtures
            .withDatabaseTestHelper(databaseTestHelper)
            .aTestChargeNew()
            .withTransactionId(chargeId)
            .withCreatedDate(creationDate)
            .withTestPaymentRequest(insert)
            .insert();
    }
}
