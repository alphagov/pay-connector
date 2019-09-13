package uk.gov.pay.connector.it.dao;

import com.google.common.collect.Lists;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import uk.gov.pay.commons.model.charge.ExternalMetadata;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.dao.SearchParams;
import uk.gov.pay.connector.charge.model.CardHolderName;
import uk.gov.pay.connector.charge.model.FirstDigitsCardNumber;
import uk.gov.pay.connector.charge.model.LastDigitsCardNumber;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.domain.ParityCheckStatus;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.it.dao.DatabaseFixtures.TestCharge;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.util.DateTimeUtils;
import uk.gov.pay.connector.util.RandomIdGenerator;

import java.io.IOException;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.time.ZonedDateTime.now;
import static java.util.Arrays.asList;
import static junit.framework.TestCase.assertTrue;
import static org.apache.commons.lang.math.RandomUtils.nextLong;
import static org.exparity.hamcrest.date.ZonedDateTimeMatchers.within;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED_RETRY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCELLED;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_CREATED;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_STARTED;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;

public class ChargeDaoIT extends DaoITestBase {

    private static final String FROM_DATE = "2016-01-01T01:00:00Z";
    private static final String TO_DATE = "2026-01-08T01:00:00Z";
    private static final String DESCRIPTION = "Test description";

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    private ChargeDao chargeDao;

    private DatabaseFixtures.TestAccount defaultTestAccount;
    private DatabaseFixtures.TestRefund defaultTestRefund;
    private DatabaseFixtures.TestCharge defaultTestCharge;
    private DatabaseFixtures.TestCardDetails defaultTestCardDetails;

    @Before
    public void setUp() {
        chargeDao = env.getInstance(ChargeDao.class);
        defaultTestCardDetails = new DatabaseFixtures(databaseTestHelper).validTestCardDetails();
        insertTestAccount();
    }

    @After
    public void clear() {
        databaseTestHelper.truncateAllData();
    }

    @Test
    public void searchChargesByGatewayAccountIdOnly() {
        // given
        insertTestCharge();
        SearchParams params = new SearchParams()
                .withGatewayAccountId(defaultTestAccount.getAccountId());

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(params);

        // then
        assertThat(charges.size(), is(1));
        ChargeEntity charge = charges.get(0);
        assertThat(charge.getId(), is(defaultTestCharge.getChargeId()));
        assertThat(charge.getAmount(), is(defaultTestCharge.getAmount()));
        assertThat(charge.getReference(), is(defaultTestCharge.getReference()));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getStatus(), is(defaultTestCharge.getChargeStatus().toString()));
        assertThat(charge.getCardDetails().getCardBrand(), is(defaultTestCardDetails.getCardBrand()));

        assertDateMatch(charge.getCreatedDate().toString());
    }

    @Test
    public void searchChargesByFullEmailMatch() {
        // given
        insertTestCharge();
        SearchParams params = new SearchParams()
                .withEmailLike(defaultTestCharge.getEmail());

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(params);

        // then
        assertThat(charges.size(), is(1));
        ChargeEntity charge = charges.get(0);
        assertCharge(charge);
    }

    @Test
    public void searchChargesByPartialEmailMatch() {
        // given
        TestCharge testCharge = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withEmail("bitcoin@blockchain.info")
                .withTestAccount(defaultTestAccount)
                .insert();
        defaultTestCardDetails
                .withChargeId(testCharge.chargeId)
                .update();
        SearchParams params = new SearchParams()
                .withEmailLike("bitcoin");

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(params);

        // then
        assertThat(charges.size(), is(1));
        ChargeEntity charge = charges.get(0);
        assertCharge(defaultTestCardDetails.getCardBrand(), testCharge, charge);
    }

    @Test
    public void searchChargesByFullCardHolderNameMatch() {
        // given
        Long chargeId = nextLong();
        TestCharge testCharge = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withCardDetails(defaultTestCardDetails.withChargeId(chargeId).withCardHolderName("Mr Satoshi"))
                .withChargeId(chargeId)
                .insert();
        SearchParams params = new SearchParams()
                .withCardHolderNameLike(CardHolderName.of(testCharge.cardDetails.getCardHolderName()));

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(params);

        // then
        assertThat(charges.size(), is(1));
        ChargeEntity charge = charges.get(0);
        assertCharge("visa", testCharge, charge);
        assertThat(charge.getCardDetails().getCardHolderName(), is(testCharge.cardDetails.getCardHolderName()));
        assertThat(charge.getCardDetails().getLastDigitsCardNumber(), is(testCharge.cardDetails.getLastDigitsCardNumber()));
    }

    @Test
    public void searchChargesByPartialCardHolderNameMatch() {
        // given
        String cardHolderName = "Mr. McPayment";
        Long chargeId = nextLong();
        TestCharge testCharge = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withCardDetails(defaultTestCardDetails
                        .withChargeId(chargeId)
                        .withCardHolderName(cardHolderName))
                .withChargeId(chargeId)
                .insert();
        SearchParams params = new SearchParams()
                .withCardHolderNameLike(CardHolderName.of("pay"));

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(params);

        // then
        assertThat(charges.size(), is(1));
        ChargeEntity charge = charges.get(0);
        assertCharge("visa", testCharge, charge);
        assertThat(charge.getCardDetails().getCardHolderName(), is(cardHolderName));
        assertThat(charge.getCardDetails().getLastDigitsCardNumber(), is(testCharge.cardDetails.getLastDigitsCardNumber()));
    }

    @Test
    public void searchChargesByFullLastFourDigits() {
        // given
        String cardHolderName = "Mr. McPayment";
        LastDigitsCardNumber lastDigits = LastDigitsCardNumber.of("4321");
        Long chargeId = nextLong();
        TestCharge testCharge = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withCardDetails(defaultTestCardDetails
                        .withChargeId(chargeId)
                        .withCardHolderName(cardHolderName)
                        .withLastDigitsOfCardNumber(lastDigits.toString()))
                .withChargeId(chargeId)
                .insert();
        SearchParams params = new SearchParams()
                .withLastDigitsCardNumber(lastDigits);

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(params);

        // then
        assertThat(charges.size(), is(1));
        ChargeEntity charge = charges.get(0);
        assertCharge("visa", testCharge, charge);
        assertThat(charge.getCardDetails().getCardHolderName(), is(cardHolderName));
        assertThat(charge.getCardDetails().getLastDigitsCardNumber(), is(lastDigits));
    }

    @Test
    public void searchChargesByFullFirstSixDigits() {
        // given
        String cardHolderName = "Mr. McPayment";
        FirstDigitsCardNumber firstSixDigits = FirstDigitsCardNumber.of("654321");
        Long chargeId = nextLong();
        TestCharge testCharge = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withCardDetails(defaultTestCardDetails
                        .withChargeId(chargeId)
                        .withCardHolderName(cardHolderName)
                        .withFirstDigitsOfCardNumber(firstSixDigits.toString()))
                .withChargeId(chargeId)
                .insert();
        SearchParams params = new SearchParams()
                .withFirstDigitsCardNumber(firstSixDigits);

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(params);

        // then
        assertThat(charges.size(), is(1));
        ChargeEntity charge = charges.get(0);
        assertCharge("visa", testCharge, charge);
        assertThat(charge.getCardDetails().getCardHolderName(), is(cardHolderName));
        assertThat(charge.getCardDetails().getFirstDigitsCardNumber(), is(firstSixDigits));
    }

    @Test
    public void searchChargesByCardBrandOnly() {
        // given
        insertTestCharge();
        SearchParams params = new SearchParams()
                .withCardBrand(defaultTestCardDetails.getCardBrand());

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(params);

        // then
        assertThat(charges.size(), is(1));
        ChargeEntity charge = charges.get(0);
        assertCharge(charge);
    }

    @Test
    public void searchChargesByMultipleCardBrandOnly() {
        // given
        String visa = "visa";
        String masterCard = "master-card";

        DatabaseFixtures.TestCharge testCharge1 = insertTestChargeForCardBrand(visa);
        DatabaseFixtures.TestCharge testCharge2 = insertTestChargeForCardBrand(masterCard);

        SearchParams params = new SearchParams()
                .withCardBrands(asList(visa, masterCard));

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(params);

        // then
        assertThat(charges.size(), is(2));
        assertCharge(masterCard, testCharge2, charges.get(0));
        assertCharge(visa, testCharge1, charges.get(1));
    }

    @Test
    public void searchChargesWithDefaultSizeAndPage_shouldGetChargesInCreationDateOrder() {
        // given
        TestCharge testCharge1 = insertNewChargeWithId(nextLong(), now().plusHours(1));
        TestCharge testCharge2 = insertNewChargeWithId(nextLong(), now().plusHours(2));
        TestCharge testCharge3 = insertNewChargeWithId(nextLong(), now().plusHours(3));
        TestCharge testCharge4 = insertNewChargeWithId(nextLong(), now().plusHours(4));
        TestCharge testCharge5 = insertNewChargeWithId(nextLong(), now().plusHours(5));
        SearchParams params = new SearchParams()
                .withGatewayAccountId(defaultTestAccount.getAccountId());

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(params);

        // then
        assertThat(charges.size(), is(5));
        assertThat(charges.get(0).getId(), is(testCharge5.getChargeId()));
        assertThat(charges.get(1).getId(), is(testCharge4.getChargeId()));
        assertThat(charges.get(2).getId(), is(testCharge3.getChargeId()));
        assertThat(charges.get(3).getId(), is(testCharge2.getChargeId()));
        assertThat(charges.get(4).getId(), is(testCharge1.getChargeId()));
    }

    @Test
    public void searchChargesWithSizeAndPageSetshouldGetChargesInCreationDateOrder() {
        // given
        TestCharge testCharge1 = insertNewChargeWithId(nextLong(), now().plusHours(1));
        TestCharge testCharge2 = insertNewChargeWithId(nextLong(), now().plusHours(2));
        TestCharge testCharge3 = insertNewChargeWithId(nextLong(), now().plusHours(3));
        TestCharge testCharge4 = insertNewChargeWithId(nextLong(), now().plusHours(4));
        TestCharge testCharge5 = insertNewChargeWithId(nextLong(), now().plusHours(5));
        TestCharge testCharge6 = insertNewChargeWithId(nextLong(), now().plusHours(6));
        TestCharge testCharge7 = insertNewChargeWithId(nextLong(), now().plusHours(7));
        TestCharge testCharge8 = insertNewChargeWithId(nextLong(), now().plusHours(8));

        // when
        SearchParams params = new SearchParams()
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withPage(1L)
                .withDisplaySize(3L);
        List<ChargeEntity> charges = chargeDao.findAllBy(params);
        // then
        assertThat(charges.size(), is(3));
        assertThat(charges.get(0).getId(), is(testCharge8.getChargeId()));
        assertThat(charges.get(1).getId(), is(testCharge7.getChargeId()));
        assertThat(charges.get(2).getId(), is(testCharge6.getChargeId()));

        // when
        params = new SearchParams()
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withPage(2L)
                .withDisplaySize(3L);
        charges = chargeDao.findAllBy(params);
        // then
        assertThat(charges.size(), is(3));
        assertThat(charges.get(0).getId(), is(testCharge5.getChargeId()));
        assertThat(charges.get(1).getId(), is(testCharge4.getChargeId()));
        assertThat(charges.get(2).getId(), is(testCharge3.getChargeId()));

        // when
        params = new SearchParams()
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withPage(3L)
                .withDisplaySize(3L);
        charges = chargeDao.findAllBy(params);
        // then
        assertThat(charges.size(), is(2));
        assertThat(charges.get(0).getId(), is(testCharge2.getChargeId()));
        assertThat(charges.get(1).getId(), is(testCharge1.getChargeId()));
    }

    @Test
    public void shouldGetTotalCount_5_when_displaySizeIs_2() {
        // given
        insertNewChargeWithId(700L, now().plusHours(1));
        insertNewChargeWithId(800L, now().plusHours(2));
        insertNewChargeWithId(900L, now().plusHours(3));
        insertNewChargeWithId(600L, now().plusHours(4));
        insertNewChargeWithId(500L, now().plusHours(5));
        SearchParams params = new SearchParams()
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withExternalState(EXTERNAL_CREATED.getStatus())
                .withDisplaySize(2L);

        // when
        Long count = chargeDao.getTotalFor(params);

        // then gets the count(*) irrespective of the max results (display_size)
        assertThat("total count for transactions mismatch", count, is(5L));
    }

    @Test
    public void searchChargesByFullReferenceOnly() {
        // given
        insertTestCharge();
        SearchParams params = new SearchParams()
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withReference(defaultTestCharge.getReference());

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(params);

        // then
        assertThat(charges.size(), is(1));

        ChargeEntity charge = charges.get(0);
        assertThat(charge.getId(), is(defaultTestCharge.getChargeId()));
        assertThat(charge.getAmount(), is(defaultTestCharge.getAmount()));
        assertThat(charge.getReference(), is(defaultTestCharge.getReference()));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getStatus(), is(defaultTestCharge.getChargeStatus().toString()));
        assertThat(charge.getCardDetails().getCardBrand(), is(defaultTestCardDetails.getCardBrand()));

        assertDateMatch(charge.getCreatedDate().toString());
    }

    @Test
    public void searchChargesByReferenceAndEmail_with_under_score() {
        // since '_' have special meaning in like queries of postgres this was resulting in undesired results
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withEmail("under_score@mail.com")
                .insert();

        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withEmail("undertaker@mail.com")
                .insert();

        SearchParams params = new SearchParams()
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withEmailLike("under_");

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(params);

        // then
        assertThat(charges.size(), is(1));

        ChargeEntity charge = charges.get(0);
        assertThat(charge.getEmail(), is("under_score@mail.com"));
    }

    @Test
    public void searchChargesByReferenceWithBackslash() {
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

        SearchParams params = new SearchParams()
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withReference(ServicePaymentReference.of("backslash\\ref"));

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(params);

        // then
        assertThat(charges.size(), is(1));

        ChargeEntity charge = charges.get(0);
        assertThat(charge.getReference(), is(ServicePaymentReference.of("backslash\\ref")));
    }

    @Test
    public void searchChargesByReferenceAndEmailShouldBeCaseInsensitive() {
        // fix that the reference and email searches should be case insensitive
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withEmail("email-id@mail.com")
                .withReference(ServicePaymentReference.of("case-Insensitive-ref"))
                .insert();

        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withEmail("EMAIL-ID@MAIL.COM")
                .withReference(ServicePaymentReference.of("Case-inSENSITIVE-Ref"))
                .insert();

        SearchParams params = new SearchParams()
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withReference(ServicePaymentReference.of("cASe-insEnsiTIve-ref"))
                .withEmailLike("EMAIL-ID@mail.com");

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(params);

        // then
        assertThat(charges.size(), is(2));

        ChargeEntity charge = charges.get(0);
        assertThat(charge.getEmail(), is("EMAIL-ID@MAIL.COM"));

        charge = charges.get(1);
        assertThat(charge.getEmail(), is("email-id@mail.com"));
    }

    @Test
    public void aBasicTestAgainstSqlInjection() {
        // given
        insertTestCharge();
        SearchParams params = new SearchParams()
                .withEmailLike("alice.111@mail.test");
        // when passed in a simple reference string
        List<ChargeEntity> charges = chargeDao.findAllBy(params);
        // then it fetches a single result
        assertThat(charges.size(), is(1));

        // when passed in a non existent reference with an sql injected string
        String sqlInjectionEmailString = "alice.111@mail.test%' or 1=1 or c.email like '%1";
        params = new SearchParams()
                .withEmailLike(sqlInjectionEmailString);
        charges = chargeDao.findAllBy(params);
        // then it fetches no result
        // with a typical sql injection vulnerable query doing this should fetch all results
        assertThat(charges.size(), is(0));
    }


    @Test
    public void searchChargeByReferenceAndLegacyStatusOnly() {
        // given
        insertTestCharge();
        SearchParams params = new SearchParams()
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withReference(defaultTestCharge.getReference())
                .withExternalState(EXTERNAL_CREATED.getStatus());

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(params);

        // then
        assertThat(charges.size(), is(1));
        ChargeEntity charge = charges.get(0);

        assertThat(charge.getId(), is(defaultTestCharge.getChargeId()));
        assertThat(charge.getAmount(), is(defaultTestCharge.getAmount()));
        assertThat(charge.getReference(), is(defaultTestCharge.getReference()));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getStatus(), is(defaultTestCharge.getChargeStatus().toString()));
        assertThat(charge.getCardDetails().getCardBrand(), is(defaultTestCardDetails.getCardBrand()));
        assertDateMatch(charge.getCreatedDate().toString());
    }

    @Test
    public void searchChargeByReferenceAndStatusAndFromDateAndToDate() {

        // given
        insertTestCharge();
        SearchParams params = new SearchParams()
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withReference(defaultTestCharge.getReference())
                .withExternalState(EXTERNAL_CREATED.getStatus())
                .withFromDate(ZonedDateTime.parse(FROM_DATE))
                .withToDate(ZonedDateTime.parse(TO_DATE));

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(params);

        // then
        assertThat(charges.size(), is(1));
        ChargeEntity charge = charges.get(0);

        assertThat(charge.getId(), is(defaultTestCharge.getChargeId()));
        assertThat(charge.getAmount(), is(defaultTestCharge.getAmount()));
        assertThat(charge.getReference(), is(defaultTestCharge.getReference()));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getStatus(), is(defaultTestCharge.getChargeStatus().toString()));
        assertThat(charge.getCardDetails().getCardBrand(), is(defaultTestCardDetails.getCardBrand()));
        assertDateMatch(charge.getCreatedDate().toString());
    }

    @Test
    public void searchChargeByReferenceAndStatusAndFromDate() {

        // given
        insertTestCharge();
        SearchParams params = new SearchParams()
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withReference(defaultTestCharge.getReference())
                .withExternalState(EXTERNAL_CREATED.getStatus())
                .withFromDate(ZonedDateTime.parse(FROM_DATE));

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(params);

        // then
        assertThat(charges.size(), is(1));
        ChargeEntity charge = charges.get(0);

        assertThat(charge.getId(), is(defaultTestCharge.getChargeId()));
        assertThat(charge.getAmount(), is(defaultTestCharge.getAmount()));
        assertThat(charge.getReference(), is(defaultTestCharge.getReference()));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getStatus(), is(defaultTestCharge.getChargeStatus().toString()));
        assertThat(charge.getCardDetails().getCardBrand(), is(defaultTestCardDetails.getCardBrand()));

        assertDateMatch(charge.getCreatedDate().toString());
    }

    @Test
    public void searchChargeByMultipleStatuses() {

        // given
        insertTestCharge();
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withChargeId(12345)
                .withTestAccount(defaultTestAccount)
                .withChargeStatus(ENTERING_CARD_DETAILS)
                .insert();

        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withChargeId(12346)
                .withTestAccount(defaultTestAccount)
                .withChargeStatus(AUTHORISATION_READY)
                .insert();

        SearchParams params = new SearchParams()
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withReference(defaultTestCharge.getReference())
                .withExternalState(EXTERNAL_STARTED.getStatus())
                .withFromDate(ZonedDateTime.parse(FROM_DATE));

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(params);

        // then
        assertThat(charges.size(), is(2));
        assertThat(charges.get(0).getStatus(), is(AUTHORISATION_READY.getValue()));
        assertThat(charges.get(1).getStatus(), is(ENTERING_CARD_DETAILS.getValue()));
    }

    @Test
    public void searchChargesShouldBeOrderedByCreationDateDescending() {
        // given
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withChargeId(555L)
                .withTestAccount(defaultTestAccount)
                .insert();

        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withChargeId(557L)
                .withTestAccount(defaultTestAccount)
                .insert();

        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withChargeId(556L)
                .withTestAccount(defaultTestAccount)
                .insert();

        SearchParams params = new SearchParams()
                .withGatewayAccountId(defaultTestAccount.getAccountId());

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(params);

        // then
        assertThat(charges.size(), is(3));
        assertThat(charges.get(0).getId(), is(556L));
        assertThat(charges.get(1).getId(), is(557L));
        assertThat(charges.get(2).getId(), is(555L));
    }

    @Test
    public void searchChargeByReferenceAndStatusAndEmailAndCardBrandAndFromDateAndToDate() {
        // given
        insertTestCharge();
        SearchParams params = new SearchParams()
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withReference(defaultTestCharge.getReference())
                .withExternalState(EXTERNAL_CREATED.getStatus())
                .withCardBrand(defaultTestCardDetails.getCardBrand())
                .withEmailLike(defaultTestCharge.getEmail())
                .withFromDate(ZonedDateTime.parse(FROM_DATE))
                .withToDate(ZonedDateTime.parse(TO_DATE));

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(params);

        // then
        assertThat(charges.size(), is(1));
        ChargeEntity charge = charges.get(0);

        assertThat(charge.getId(), is(defaultTestCharge.getChargeId()));
        assertThat(charge.getAmount(), is(defaultTestCharge.getAmount()));
        assertThat(charge.getReference(), is(defaultTestCharge.getReference()));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getStatus(), is(defaultTestCharge.getChargeStatus().toString()));
        assertThat(charge.getCardDetails().getCardBrand(), is(defaultTestCardDetails.getCardBrand()));
        assertDateMatch(charge.getCreatedDate().toString());
    }

    @Test
    public void searchChargeByReferenceAndStatusAndToDate() {

        // given
        insertTestCharge();
        SearchParams params = new SearchParams()
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withReference(defaultTestCharge.getReference())
                .withExternalState(EXTERNAL_CREATED.getStatus())
                .withToDate(ZonedDateTime.parse(TO_DATE));

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(params);

        // then
        assertThat(charges.size(), is(1));
        ChargeEntity charge = charges.get(0);

        assertThat(charge.getId(), is(defaultTestCharge.getChargeId()));
        assertThat(charge.getAmount(), is(defaultTestCharge.getAmount()));
        assertThat(charge.getReference(), is(defaultTestCharge.getReference()));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getStatus(), is(defaultTestCharge.getChargeStatus().toString()));
        assertThat(charge.getCardDetails().getCardBrand(), is(defaultTestCardDetails.getCardBrand()));
        assertDateMatch(charge.getCreatedDate().toString());
    }

    @Test
    public void searchChargeByReferenceAndStatusAndFromDate_ShouldReturnZeroIfDateIsNotInRange() {
        insertTestCharge();

        SearchParams params = new SearchParams()
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withReference(defaultTestCharge.getReference())
                .withExternalState(EXTERNAL_CREATED.getStatus())
                .withFromDate(ZonedDateTime.parse(TO_DATE));

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(params);

        assertThat(charges.size(), is(0));
    }

    @Test
    public void searchChargeByReferenceAndStatusAndToDate_ShouldReturnZeroIfToDateIsNotInRange() {
        insertTestCharge();
        SearchParams params = new SearchParams()
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withReference(defaultTestCharge.getReference())
                .withExternalState(EXTERNAL_CREATED.getStatus())
                .withToDate(ZonedDateTime.parse(FROM_DATE));

        List<ChargeEntity> charges = chargeDao.findAllBy(params);

        assertThat(charges.size(), is(0));
    }

    @Test
    public void searchChargeByPartialReference_ShouldReturnCharge() {
        insertTestChargeWithReference(ServicePaymentReference.of("willnotreturn"));
        insertTestChargeWithReference(ServicePaymentReference.of("Test reference"));
        SearchParams searchParams = new SearchParams()
                .withReferenceLike(ServicePaymentReference.of("reference"));

        List<ChargeEntity> charges = chargeDao.findAllBy(searchParams);

        assertThat(charges.size(), is(1));
        assertThat(charges.get(0).getReference(), is(defaultTestCharge.getReference()));
    }

    @Test
    public void searchChargeByPartialCaseInsensitiveReference_ShouldReturnCharge() {
        insertTestChargeWithReference(ServicePaymentReference.of("willnotreturn"));
        insertTestChargeWithReference(ServicePaymentReference.of("Test reference"));
        SearchParams searchParams = new SearchParams()
                .withReferenceLike(ServicePaymentReference.of("rEfErEnCe"));

        List<ChargeEntity> charges = chargeDao.findAllBy(searchParams);

        assertThat(charges.size(), is(1));
        assertThat(charges.get(0).getReference(), is(defaultTestCharge.getReference()));
    }

    @Test
    public void searchChargeByReference_ShouldReturnCharge() {
        insertTestChargeWithReference(ServicePaymentReference.of("willnotreturn"));
        insertTestChargeWithReference(ServicePaymentReference.of("Test reference"));
        SearchParams searchParams = new SearchParams()
                .withReference(ServicePaymentReference.of("Test reference"));

        List<ChargeEntity> charges = chargeDao.findAllBy(searchParams);

        assertThat(charges.size(), is(1));
        assertThat(charges.get(0).getReference(), is(defaultTestCharge.getReference()));
    }

    @Test
    public void searchChargeByReference_ShouldNotReturnChargeWithPartialMatch() {
        insertTestChargeWithReference(ServicePaymentReference.of("willnotreturn"));
        insertTestChargeWithReference(ServicePaymentReference.of("Test reference"));
        SearchParams searchParams = new SearchParams()
                .withReference(ServicePaymentReference.of("reference"));

        List<ChargeEntity> charges = chargeDao.findAllBy(searchParams);

        assertThat(charges.size(), is(0));
    }

    private void assertDateMatch(String createdDateString) {
        assertDateMatch(DateTimeUtils.toUTCZonedDateTime(createdDateString).get());
    }

    private void assertDateMatch(ZonedDateTime createdDateTime) {
        assertThat(createdDateTime, within(1, ChronoUnit.MINUTES, now()));
    }

    @Test
    public void chargeEvents_shouldRecordTransactionIdWithEachStatusChange() {
        Long chargeId = 56735L;
        String externalChargeId = "charge456";

        String transactionId = "345654";

        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withTransactionId(transactionId)
                .insert();

        Optional<ChargeEntity> charge = chargeDao.findById(chargeId);
        ChargeEntity entity = charge.get();
        entity.setStatus(ENTERING_CARD_DETAILS);

    }

    @Test
    public void invalidSizeOfReference() {
        expectedEx.expect(RuntimeException.class);

        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity(defaultTestAccount.getPaymentProvider(), new HashMap<>(), TEST);
        gatewayAccount.setId(defaultTestAccount.getAccountId());
        chargeDao.persist(aValidChargeEntity().withReference(ServicePaymentReference.of(RandomStringUtils.randomAlphanumeric(255))).build());
    }

    @Test
    public void shouldCreateANewCharge() {

        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity(defaultTestAccount.getPaymentProvider(), new HashMap<>(), TEST);
        gatewayAccount.setId(defaultTestAccount.getAccountId());

        ChargeEntity chargeEntity = aValidChargeEntity()
                .withId(null)
                .withGatewayAccountEntity(gatewayAccount)
                .build();

        assertThat(chargeEntity.getId(), is(nullValue()));

        chargeDao.persist(chargeEntity);

        assertThat(chargeEntity.getId(), is(notNullValue()));
    }

    @Test
    public void shouldCreateANewChargeWith3dsDetails() {
        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity(defaultTestAccount.getPaymentProvider(), new HashMap<>(), TEST);
        gatewayAccount.setId(defaultTestAccount.getAccountId());

        String paRequest = "3dsPaRequest";
        String issuerUrl = "https://issuer.example.com/3ds";
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withId(null)
                .withGatewayAccountEntity(gatewayAccount)
                .withPaRequest(paRequest)
                .withIssuerUrl(issuerUrl)
                .build();

        assertThat(chargeEntity.getId(), is(nullValue()));

        chargeDao.persist(chargeEntity);

        Optional<ChargeEntity> charge = chargeDao.findById(chargeEntity.getId());

        assertThat(charge.get().get3dsDetails().getPaRequest(), is(paRequest));
        assertThat(charge.get().get3dsDetails().getIssuerUrl(), is(issuerUrl));
    }

    @Test
    public void shouldCreateANewChargeWithProviderSessionId() {
        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity(defaultTestAccount.getPaymentProvider(), new HashMap<>(), TEST);
        gatewayAccount.setId(defaultTestAccount.getAccountId());

        String providerSessionId = "provider-session-id-value";
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withId(null)
                .withGatewayAccountEntity(gatewayAccount)
                .withProviderSessionId(providerSessionId)
                .build();

        assertThat(chargeEntity.getId(), is(nullValue()));

        chargeDao.persist(chargeEntity);

        Optional<ChargeEntity> charge = chargeDao.findById(chargeEntity.getId());

        assertThat(charge.get().getProviderSessionId(), is(providerSessionId));
    }

    @Test
    public void shouldCreateANewChargeWithExternalMetadata() throws IOException {
        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity(defaultTestAccount.getPaymentProvider(), new HashMap<>(), TEST);
        gatewayAccount.setId(defaultTestAccount.getAccountId());

        ExternalMetadata expectedExternalMetadata = new ExternalMetadata(
                Map.of("key1", "String1",
                        "key2", 123,
                        "key3", true));

        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withExternalMetadata(expectedExternalMetadata)
                .build();

        chargeDao.persist(chargeEntity);
        chargeDao.forceRefresh(chargeEntity);
        Optional<ChargeEntity> charge = chargeDao.findById(chargeEntity.getId());

        assertThat(charge.get().getExternalMetadata().get().getMetadata(), equalTo(expectedExternalMetadata.getMetadata()));
    }

    @Test
    public void shouldCreateNewChargeWithParityCheckStatus() {
        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity(defaultTestAccount.getPaymentProvider(), new HashMap<>(), TEST);
        gatewayAccount.setId(defaultTestAccount.getAccountId());

        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withParityStatus(ParityCheckStatus.EXISTS_IN_LEDGER)
                .build();

        chargeDao.persist(chargeEntity);
        chargeDao.forceRefresh(chargeEntity);
        Optional<ChargeEntity> charge = chargeDao.findById(chargeEntity.getId());

        assertThat(charge.get().getParityCheckStatus(), equalTo(ParityCheckStatus.EXISTS_IN_LEDGER));
        assertThat(charge.get().getParityCheckDate(), is(notNullValue()));
    }

    @Test
    public void shouldReturnNullFindingByIdWhenChargeDoesNotExist() {

        Optional<ChargeEntity> charge = chargeDao.findById(5686541L);

        assertThat(charge.isPresent(), is(false));
    }

    @Test
    public void shouldFindChargeEntityByProviderAndTransactionId() {

        // given
        String transactionId = "7826782163";
        ZonedDateTime createdDate = now(ZoneId.of("UTC"));
        Long chargeId = 9999L;
        String externalChargeId = "charge9999";

        DatabaseFixtures.TestCharge testCharge = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(chargeId)
                .withCreatedDate(createdDate)
                .withExternalChargeId(externalChargeId)
                .withTransactionId(transactionId)
                .insert();
        DatabaseFixtures.TestCardDetails testCardDetails = DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper)
                .validTestCardDetails()
                .withChargeId(chargeId)
                .update();

        // when
        Optional<ChargeEntity> chargeOptional = chargeDao.findByProviderAndTransactionId(defaultTestAccount.getPaymentProvider(), transactionId);

        // then
        assertThat(chargeOptional.isPresent(), is(true));

        ChargeEntity charge = chargeOptional.get();
        assertThat(charge.getId(), is(chargeId));
        assertThat(charge.getGatewayTransactionId(), is(transactionId));
        assertThat(charge.getReturnUrl(), is(testCharge.getReturnUrl()));
        assertThat(charge.getStatus(), is(CREATED.getValue()));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getCreatedDate(), is(createdDate));
        assertThat(charge.getReference(), is(testCharge.getReference()));
        assertThat(charge.getGatewayAccount(), is(notNullValue()));
        assertThat(charge.getCardDetails().getCardBrand(), is(testCardDetails.getCardBrand()));
        assertThat(charge.getExternalMetadata(), is(Optional.empty()));
    }

    @Test
    public void shouldGetGatewayAccountWhenFindingChargeEntityByProviderAndTransactionId() {

        String transactionId = "7826782163";

        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(8888L)
                .withExternalChargeId("charge8888")
                .withTransactionId(transactionId)
                .insert();

        Optional<ChargeEntity> chargeOptional = chargeDao.findByProviderAndTransactionId(defaultTestAccount.getPaymentProvider(), transactionId);

        assertThat(chargeOptional.isPresent(), is(true));

        ChargeEntity charge = chargeOptional.get();
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

        DatabaseFixtures.TestCharge testCharge = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(chargeId)
                .withCreatedDate(createdDate)
                .withExternalChargeId(externalChargeId)
                .withTransactionId(transactionId)
                .insert();
        DatabaseFixtures.TestCardDetails testCardDetails = DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper)
                .validTestCardDetails()
                .withChargeId(chargeId)
                .update();

        ChargeEntity charge = chargeDao.findByExternalIdAndGatewayAccount(externalChargeId, defaultTestAccount.getAccountId()).get();

        assertThat(charge.getId(), is(chargeId));
        assertThat(charge.getGatewayTransactionId(), is(transactionId));
        assertThat(charge.getReturnUrl(), is(testCharge.getReturnUrl()));
        assertThat(charge.getStatus(), is(testCharge.getChargeStatus().toString()));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getCreatedDate(), is(createdDate));
        assertThat(charge.getReference(), is(testCharge.getReference()));
        assertThat(charge.getGatewayAccount(), is(notNullValue()));
        assertThat(charge.getGatewayAccount().getId(), is(defaultTestAccount.getAccountId()));
        assertThat(charge.getCardDetails().getCardBrand(), is(testCardDetails.getCardBrand()));
    }

    @Test
    public void shouldGetChargeByChargeIdAsNullWhenAccountIdDoesNotMatch() {
        insertTestCharge();
        Optional<ChargeEntity> chargeForAccount = chargeDao.findByExternalIdAndGatewayAccount(defaultTestCharge.getExternalChargeId(), 456781L);
        assertThat(chargeForAccount.isPresent(), is(false));
    }

    @Test
    public void findById_shouldFindChargeEntity() {

        // given
        insertTestCharge();
        insertTestRefund();

        // when
        ChargeEntity charge = chargeDao.findById(defaultTestCharge.getChargeId()).get();

        // then
        assertThat(charge.getId(), is(defaultTestCharge.getChargeId()));
        assertThat(charge.getAmount(), is(defaultTestCharge.getAmount()));
        assertThat(charge.getReference(), is(defaultTestCharge.getReference()));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getStatus(), is(CREATED.getValue()));
        assertThat(charge.getGatewayAccount().getId(), is(defaultTestAccount.getAccountId()));
        assertThat(charge.getReturnUrl(), is(defaultTestCharge.getReturnUrl()));
        assertThat(charge.getCreatedDate(), is(defaultTestCharge.getCreatedDate()));
        assertThat(charge.getCardDetails().getCardBrand(), is(defaultTestCardDetails.getCardBrand()));

        assertThat(charge.getRefunds().size(), is(1));
        RefundEntity refund = charge.getRefunds().get(0);
        assertThat(refund.getId(), is(defaultTestRefund.getId()));
        assertThat(refund.getAmount(), is(defaultTestRefund.getAmount()));
        assertThat(refund.getStatus(), is(defaultTestRefund.getStatus()));
        assertThat(refund.getCreatedDate(), is(defaultTestRefund.getCreatedDate()));
        assertThat(refund.getChargeEntity().getId(), is(defaultTestCharge.getChargeId()));
        assertNotNull(refund.getVersion());
    }

    @Test
    public void findByExternalId_shouldFindAChargeEntity() {
        insertTestCharge();
        Optional<ChargeEntity> chargeForAccount = chargeDao.findByExternalId(defaultTestCharge.getExternalChargeId());
        assertThat(chargeForAccount.isPresent(), is(true));
    }

    @Test
    public void findByExternalId_shouldNotFindAChargeEntity() {
        Optional<ChargeEntity> chargeForAccount = chargeDao.findByExternalId("abcdefg123");
        assertThat(chargeForAccount.isPresent(), is(false));
    }

    @Test
    public void testFindByDate_status_findsValidChargeForStatus() {
        TestCharge charge = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(nextLong())
                .withExternalChargeId(RandomIdGenerator.newId())
                .withCreatedDate(now().minusHours(2))
                .insert();

        ArrayList<ChargeStatus> chargeStatuses = Lists.newArrayList(CREATED, ENTERING_CARD_DETAILS, AUTHORISATION_SUCCESS);

        List<ChargeEntity> charges = chargeDao.findBeforeDateWithStatusIn(now().minusHours(1), chargeStatuses);

        assertThat(charges.size(), is(1));
        assertEquals(charges.get(0).getId(), charge.getChargeId());
    }

    @Test
    public void testFindByDateStatus_findsNoneForValidStatus() {
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(nextLong())
                .withExternalChargeId(RandomIdGenerator.newId())
                .withCreatedDate(now().minusHours(2))
                .insert();

        ArrayList<ChargeStatus> chargeStatuses = Lists.newArrayList(CAPTURE_READY, SYSTEM_CANCELLED);

        List<ChargeEntity> charges = chargeDao.findBeforeDateWithStatusIn(now().minusHours(1), chargeStatuses);

        assertThat(charges.size(), is(0));
    }

    @Test
    public void testFindByDateStatus_findsNoneForExpiredDate() {
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(nextLong())
                .withExternalChargeId(RandomIdGenerator.newId())
                .withCreatedDate(now().minusMinutes(30))
                .insert();

        ArrayList<ChargeStatus> chargeStatuses = Lists.newArrayList(CREATED, ENTERING_CARD_DETAILS, AUTHORISATION_SUCCESS);

        List<ChargeEntity> charges = chargeDao.findBeforeDateWithStatusIn(now().minusHours(1), chargeStatuses);

        assertThat(charges.size(), is(0));
    }

    @Test
    public void findByAccountBetweenDatesWithStatusIn_findsChargeWithMatchingAccountAndStatusInsideRange() {
        TestCharge charge = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(nextLong())
                .withChargeStatus(CAPTURE_APPROVED_RETRY)
                .withCreatedDate(now().minusMinutes(30))
                .insert();

        ArrayList<ChargeStatus> statuses = Lists.newArrayList(CAPTURE_APPROVED, CAPTURE_APPROVED_RETRY, CAPTURE_READY);

        List<ChargeEntity> charges = chargeDao.findByAccountBetweenDatesWithStatusIn(
                defaultTestAccount.getAccountId(),
                now().minusMinutes(45),
                now().minusMinutes(15),
                statuses);

        assertThat(charges.size(), is(1));
        assertThat(charges.get(0).getId(), is(charge.getChargeId()));
    }

    @Test
    public void findByAccountBetweenDatesWithStatusIn_findsNoneWithNonMatchingAccountButMatchingStatusInsideRange() {
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(nextLong())
                .withChargeStatus(AUTHORISATION_SUCCESS)
                .withCreatedDate(now().minusMinutes(30))
                .insert();

        ArrayList<ChargeStatus> statuses = Lists.newArrayList(AUTHORISATION_SUCCESS, AUTHORISATION_REJECTED);

        List<ChargeEntity> charges = chargeDao.findByAccountBetweenDatesWithStatusIn(
                100_000L,
                now().minusMinutes(45),
                now().minusMinutes(15),
                statuses);

        assertThat(charges.size(), is(0));
    }

    @Test
    public void findByAccountBetweenDatesWithStatusIn_findsNoneWithMatchingAccountButNonMatchingStatusInsideRange() {
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(nextLong())
                .withChargeStatus(CAPTURED)
                .withCreatedDate(now().minusMinutes(30))
                .insert();

        ArrayList<ChargeStatus> statuses = Lists.newArrayList(CREATED, ENTERING_CARD_DETAILS);

        List<ChargeEntity> charges = chargeDao.findByAccountBetweenDatesWithStatusIn(
                defaultTestAccount.getAccountId(),
                now().minusMinutes(45),
                now().minusMinutes(15),
                statuses);

        assertThat(charges.size(), is(0));
    }

    @Test
    public void findByAccountBetweenDatesWithStatusIn_findsNoneWithMatchingAccountAndStatusBeforeRange() {
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(nextLong())
                .withChargeStatus(CAPTURED)
                .withCreatedDate(now().minusMinutes(40))
                .insert();

        ArrayList<ChargeStatus> statuses = Lists.newArrayList(CAPTURE_SUBMITTED, CAPTURED);

        List<ChargeEntity> charges = chargeDao.findByAccountBetweenDatesWithStatusIn(
                defaultTestAccount.getAccountId(),
                now().minusMinutes(30),
                now().minusMinutes(5),
                statuses);

        assertThat(charges.size(), is(0));
    }

    @Test
    public void findByAccountBetweenDatesWithStatusIn_findsNoneWithMatchingAccountAndStatusAfterRange() {
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(nextLong())
                .withChargeStatus(AUTHORISATION_SUCCESS)
                .withCreatedDate(now().minusMinutes(5))
                .insert();

        ArrayList<ChargeStatus> statuses = Lists.newArrayList(AUTHORISATION_SUCCESS, AUTHORISATION_3DS_REQUIRED);

        List<ChargeEntity> charges = chargeDao.findByAccountBetweenDatesWithStatusIn(
                defaultTestAccount.getAccountId(),
                now().minusMinutes(60),
                now().minusMinutes(30),
                statuses);

        assertThat(charges.size(), is(0));
    }

    @Test
    public void testFindChargeByUnusedTokenId() {
        TestCharge charge = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(nextLong())
                .withExternalChargeId(RandomIdGenerator.newId())
                .withCreatedDate(now())
                .withAmount(300L)
                .insert();

        databaseTestHelper.addToken(charge.getChargeId(), "some-token-id");

        Optional<ChargeEntity> chargeOpt = chargeDao.findByTokenId("some-token-id");
        assertTrue(chargeOpt.isPresent());
        assertEquals(chargeOpt.get().getExternalId(), charge.getExternalChargeId());

        assertThat(chargeOpt.get().getGatewayAccount(), is(notNullValue()));
        assertThat(chargeOpt.get().getGatewayAccount().getId(), is(defaultTestAccount.getAccountId()));
    }

    @Test
    public void testFindChargeByUsedTokenId() {
        TestCharge charge = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .insert();

        databaseTestHelper.addToken(charge.getChargeId(), "used-token-id", true);

        Optional<ChargeEntity> chargeOpt = chargeDao.findByTokenId("used-token-id");
        assertTrue(chargeOpt.isEmpty());
    }

    @Test
    public void findChargesForCapture_shouldReturnChargesInCaptureApprovedState() {
        final Long chargeId1 = nextLong();

        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(chargeId1)
                .withExternalChargeId(RandomIdGenerator.newId())
                .withCreatedDate(now().minusHours(2))
                .withChargeStatus(CAPTURE_APPROVED)
                .insert();
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestChargeEvent()
                .withChargeId(chargeId1)
                .withChargeStatus(CAPTURE_APPROVED)
                .withDate(now().minusMinutes(61))
                .insert();


        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(nextLong())
                .withExternalChargeId(RandomIdGenerator.newId())
                .withCreatedDate(now().minusHours(2))
                .withChargeStatus(CAPTURE_READY)
                .insert();

        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(nextLong())
                .withExternalChargeId(RandomIdGenerator.newId())
                .withCreatedDate(now().minusHours(2))
                .withChargeStatus(CAPTURE_SUBMITTED)
                .insert();

        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(104L)
                .withExternalChargeId("ext-id4")
                .withCreatedDate(now().minusHours(2))
                .withChargeStatus(CAPTURED)
                .insert();

        List<ChargeEntity> charges = chargeDao.findChargesForCapture(10, Duration.of(60, ChronoUnit.MINUTES));

        assertThat(charges.size(), is(1));
        assertEquals(charges.get(0).getId(), chargeId1);
    }

    @Test
    public void findChargesForCapture_shouldNotReturnAChargeForWhichCaptureHasBeenAttemptedRecently() {
        final long chargeId1 = nextLong();
        final long chargeId2 = nextLong();

        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(chargeId1)
                .withExternalChargeId(RandomIdGenerator.newId())
                .withCreatedDate(now().minusHours(2))
                .withChargeStatus(CAPTURE_APPROVED_RETRY)
                .insert();
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestChargeEvent()
                .withChargeId(chargeId1)
                .withChargeStatus(CAPTURE_APPROVED)
                .withDate(now().minusMinutes(63))
                .insert();
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestChargeEvent()
                .withChargeId(chargeId1)
                .withChargeStatus(CAPTURE_APPROVED_RETRY)
                .withDate(now().minusMinutes(62))
                .insert();
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestChargeEvent()
                .withChargeId(chargeId1)
                .withChargeStatus(CAPTURE_APPROVED_RETRY)
                .withDate(now().minusMinutes(61))
                .insert();

        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(chargeId2)
                .withExternalChargeId(RandomIdGenerator.newId())
                .withCreatedDate(now().minusHours(2))
                .withChargeStatus(CAPTURE_APPROVED_RETRY)
                .insert();
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestChargeEvent()
                .withChargeId(chargeId2)
                .withChargeStatus(CAPTURE_APPROVED)
                .withDate(now().minusMinutes(63))
                .insert();
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestChargeEvent()
                .withChargeId(chargeId2)
                .withChargeStatus(CAPTURE_APPROVED_RETRY)
                .withDate(now().minusMinutes(62))
                .insert();
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestChargeEvent()
                .withChargeId(chargeId2)
                .withChargeStatus(CAPTURE_APPROVED_RETRY)
                .withDate(now().minusMinutes(59))
                .insert();


        List<ChargeEntity> charges = chargeDao.findChargesForCapture(10, Duration.of(60, ChronoUnit.MINUTES));

        assertThat(charges.size(), is(1));
        assertEquals(charges.get(0).getId(), new Long(chargeId1));
    }

    @Test
    public void countChargesForCapture_shouldReturnNumberOfChargesInCaptureApprovedState() {
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(nextLong())
                .withExternalChargeId(RandomIdGenerator.newId())
                .withCreatedDate(now().minusHours(2))
                .withChargeStatus(CAPTURE_APPROVED)
                .insert();
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(nextLong())
                .withExternalChargeId(RandomIdGenerator.newId())
                .withCreatedDate(now().minusHours(2))
                .withChargeStatus(CAPTURE_APPROVED_RETRY)
                .insert();
        TestCharge charge = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(nextLong())
                .withExternalChargeId(RandomIdGenerator.newId())
                .withCreatedDate(now())
                .withChargeStatus(CAPTURE_APPROVED_RETRY)
                .insert();
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestChargeEvent()
                .withChargeId(charge.getChargeId())
                .withDate(now())
                .withChargeStatus(CAPTURE_APPROVED_RETRY)
                .insert();

        assertThat(chargeDao.countChargesForImmediateCapture(Duration.ofHours(1)), is(2));
    }

    @Test
    public void countCaptureRetriesForChargeExternalId_shouldReturnNumberOfRetries() {
        long chargeId = nextLong();
        String externalChargeId = RandomIdGenerator.newId();

        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withCreatedDate(now().minusHours(2))
                .withChargeStatus(CAPTURE_APPROVED)
                .insert();

        assertThat(chargeDao.countCaptureRetriesForChargeExternalId(externalChargeId), is(0));

        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestChargeEvent()
                .withChargeId(chargeId)
                .withChargeStatus(CAPTURE_APPROVED)
                .insert();
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestChargeEvent()
                .withChargeId(chargeId)
                .withChargeStatus(CAPTURE_APPROVED_RETRY)
                .insert();

        assertThat(chargeDao.countCaptureRetriesForChargeExternalId(externalChargeId), is(2));
    }

    @Test
    public void findByIdAndLimit() {
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(nextLong())
                .withExternalChargeId(RandomIdGenerator.newId())
                .withCreatedDate(now().minusHours(2))
                .withChargeStatus(CAPTURE_APPROVED)
                .insert();
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(nextLong())
                .withExternalChargeId(RandomIdGenerator.newId())
                .withCreatedDate(now().minusHours(2))
                .withChargeStatus(CAPTURE_APPROVED_RETRY)
                .insert();

        assertThat(chargeDao.findByIdAndLimit(0L, 2).size(), is(2));
    }

    @Test
    public void findByGatewayTransactionId() {
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withExternalChargeId("some-external-id")
                .withTransactionId("gateway-transaction-id")
                .insert();

        ChargeEntity chargeEntity = chargeDao.
                findByGatewayTransactionId("gateway-transaction-id")
                .get();

        assertThat(chargeEntity.getExternalId(), is("some-external-id"));
    }

    @Test
    public void getChargeWithAFee_shouldReturnFeeOnCharge() {
        insertTestCharge();

        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestFee()
                .withFeeDue(100L)
                .withFeeCollected(10L)
                .withTestCharge(defaultTestCharge)
                .insert();

        assertThat(chargeDao
                        .findById(defaultTestCharge.getChargeId())
                        .flatMap(ChargeEntity::getFeeAmount)
                        .get(),
                is(10L)
        );
    }

    @Test
    public void findMaxId_returnsTheMaximumId() {
        insertTestCharge();

        assertThat(chargeDao.findMaxId(), is(defaultTestCharge.getChargeId()));
    }

    @Test
    public void findChargeByProviderId() {

        insertTestCharge();
        Optional<ChargeEntity> chargeEntity = chargeDao.findByProviderSessionId("providerId");
        assertThat(chargeEntity.isPresent(), is(true));

    }

    @Test
    public void findChargesByParityCheckStatus() {
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withParityCheckStatus(ParityCheckStatus.MISSING_IN_LEDGER)
                .insert();

        var charges = chargeDao.findByParityCheckStatus(ParityCheckStatus.MISSING_IN_LEDGER);

        assertThat(charges.size(), is(1));
        assertThat(charges.get(0).getParityCheckStatus(), is(ParityCheckStatus.MISSING_IN_LEDGER));
    }

    private void insertTestAccount() {
        this.defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .withAccountId(nextLong())
                .insert();
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

    private void insertTestChargeWithReference(ServicePaymentReference reference) {
        this.defaultTestCharge = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withReference(reference)
                .insert();
        defaultTestCardDetails
                .withChargeId(defaultTestCharge.chargeId)
                .update();
    }

    private DatabaseFixtures.TestCharge insertTestChargeForCardBrand(String cardBrand) {
        DatabaseFixtures.TestCharge testCharge = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .insert();
        defaultTestCardDetails
                .withChargeId(testCharge.chargeId)
                .withCardBrand(cardBrand)
                .update();

        return testCharge;
    }

    private void insertTestRefund() {
        this.defaultTestRefund = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestRefund()
                .withTestCharge(defaultTestCharge)
                .insert();
    }

    private DatabaseFixtures.TestCharge insertNewChargeWithId(Long chargeId, ZonedDateTime creationDate) {
        return DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withChargeId(chargeId)
                .withCreatedDate(creationDate)
                .withTestAccount(defaultTestAccount)
                .insert();
    }

    private void assertCharge(ChargeEntity charge) {
        assertCharge(defaultTestCardDetails.getCardBrand(), defaultTestCharge, charge);
    }

    private void assertCharge(String cardBrand, DatabaseFixtures.TestCharge expectedCharge, ChargeEntity actualCharge) {
        assertThat(actualCharge.getId(), is(expectedCharge.getChargeId()));
        assertThat(actualCharge.getAmount(), is(expectedCharge.getAmount()));
        assertThat(actualCharge.getReference(), is(expectedCharge.getReference()));
        assertThat(actualCharge.getEmail(), is(expectedCharge.getEmail()));
        assertThat(actualCharge.getDescription(), is(DESCRIPTION));
        assertThat(actualCharge.getStatus(), is(expectedCharge.getChargeStatus().toString()));
        assertThat(actualCharge.getCardDetails().getCardBrand(), is(cardBrand));
        assertDateMatch(actualCharge.getCreatedDate().toString());
    }
}
