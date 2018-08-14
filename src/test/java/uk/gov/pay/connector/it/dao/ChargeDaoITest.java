package uk.gov.pay.connector.it.dao;

import com.google.common.collect.Lists;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.ChargeSearchParams;
import uk.gov.pay.connector.model.ServicePaymentReference;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.RefundEntity;
import uk.gov.pay.connector.util.DateTimeUtils;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static java.time.ZonedDateTime.now;
import static java.util.Arrays.asList;
import static junit.framework.TestCase.assertTrue;
import static org.exparity.hamcrest.date.ZonedDateTimeMatchers.within;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static uk.gov.pay.connector.model.api.ExternalChargeState.EXTERNAL_CREATED;
import static uk.gov.pay.connector.model.api.ExternalChargeState.EXTERNAL_STARTED;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_READY;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_APPROVED_RETRY;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_READY;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.SYSTEM_CANCELLED;
import static uk.gov.pay.connector.model.domain.GatewayAccountEntity.Type.TEST;

public class ChargeDaoITest extends DaoITestBase {

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
    public void setUp() throws Exception {
        chargeDao = env.getInstance(ChargeDao.class);
        defaultTestCardDetails = new DatabaseFixtures(databaseTestHelper).validTestCardDetails();
        insertTestAccount();
    }

    @Test
    public void searchChargesByGatewayAccountIdOnly() throws Exception {
        // given
        insertTestCharge();
        ChargeSearchParams params = new ChargeSearchParams()
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
    public void searchChargesByFullEmailMatch() throws Exception {
        // given
        insertTestCharge();
        ChargeSearchParams params = new ChargeSearchParams()
                .withEmailLike(defaultTestCharge.getEmail());

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(params);

        // then
        assertThat(charges.size(), is(1));
        ChargeEntity charge = charges.get(0);
        assertCharge(charge);
    }

    @Test
    public void searchChargesByPartialEmailMatch() throws Exception {
        // given
        insertTestCharge();
        ChargeSearchParams params = new ChargeSearchParams()
                .withEmailLike("alice");

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(params);

        // then
        assertThat(charges.size(), is(1));
        ChargeEntity charge = charges.get(0);
        assertCharge(charge);
    }

    @Test
    public void searchChargesByCardBrandOnly() throws Exception {
        // given
        insertTestCharge();
        ChargeSearchParams params = new ChargeSearchParams()
                .withCardBrand(defaultTestCardDetails.getCardBrand());

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(params);

        // then
        assertThat(charges.size(), is(1));
        ChargeEntity charge = charges.get(0);
        assertCharge(charge);
    }

    @Test
    public void searchChargesByMultipleCardBrandOnly() throws Exception {
        // given
        String visa = "visa";
        String masterCard = "master-card";

        DatabaseFixtures.TestCharge testCharge1 = insertTestChargeForCardBrand(visa);
        DatabaseFixtures.TestCharge testCharge2 = insertTestChargeForCardBrand(masterCard);

        ChargeSearchParams params = new ChargeSearchParams()
                .withCardBrands(asList(visa, masterCard));

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(params);

        // then
        assertThat(charges.size(), is(2));
        assertCharge(masterCard, testCharge2, charges.get(0));
        assertCharge(visa, testCharge1, charges.get(1));
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
        List<ChargeEntity> charges = chargeDao.findAllBy(params);

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
        List<ChargeEntity> charges = chargeDao.findAllBy(params);
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
        charges = chargeDao.findAllBy(params);
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
        charges = chargeDao.findAllBy(params);
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
                .withExternalState(EXTERNAL_CREATED.getStatus())
                .withDisplaySize(2L);

        // when
        Long count = chargeDao.getTotalFor(params);

        // then gets the count(*) irrespective of the max results (display_size)
        assertThat("total count for transactions mismatch", count, is(5L));
    }

    @Test
    public void searchChargesByFullReferenceOnly() throws Exception {
        // given
        insertTestCharge();
        ChargeSearchParams params = new ChargeSearchParams()
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withReferenceLike(defaultTestCharge.getReference());

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
    public void searchChargesByPartialReferenceOnly() throws Exception {
        // given
        insertTestCharge();
        ServicePaymentReference paymentReference = ServicePaymentReference.of("Council Tax Payment reference 2");
        Long chargeId = System.currentTimeMillis();
        String externalChargeId = "chargeabc";

        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withCardDetails(defaultTestCardDetails.withChargeId(chargeId))
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withReference(paymentReference)
                .insert();

        String reference = "reference";
        ChargeSearchParams params = new ChargeSearchParams()
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withReferenceLike(ServicePaymentReference.of(reference));

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(params);

        // then
        assertThat(charges.size(), is(2));
        assertThat(charges.get(1).getId(), is(defaultTestCharge.getChargeId()));
        assertThat(charges.get(1).getReference(), is(defaultTestCharge.getReference()));
        assertThat(charges.get(0).getReference(), is(paymentReference));

        for (ChargeEntity charge : charges) {
            assertThat(charge.getAmount(), is(defaultTestCharge.getAmount()));
            assertThat(charge.getDescription(), is(DESCRIPTION));
            assertThat(charge.getStatus(), is(defaultTestCharge.getChargeStatus().toString()));
            assertDateMatch(charge.getCreatedDate().toString());
            assertThat(charge.getCardDetails().getCardBrand(), is(defaultTestCardDetails.getCardBrand()));
        }
    }

    @Test
    public void searchChargesByReferenceAndEmail_with_under_score() {
        // since '_' have special meaning in like queries of postgres this was resulting in undesired results
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withReference(ServicePaymentReference.of("under_score_ref"))
                .withEmail("under_score@mail.com")
                .insert();

        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withReference(ServicePaymentReference.of("understand"))
                .withEmail("undertaker@mail.com")
                .insert();

        ChargeSearchParams params = new ChargeSearchParams()
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withReferenceLike(ServicePaymentReference.of("under_"))
                .withEmailLike("under_");

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(params);

        // then
        assertThat(charges.size(), is(1));

        ChargeEntity charge = charges.get(0);
        assertThat(charge.getReference(), is(ServicePaymentReference.of("under_score_ref")));
        assertThat(charge.getEmail(), is("under_score@mail.com"));
    }

    @Test
    public void searchChargesByReferenceWithPercentSign() throws Exception {
        // since '%' have special meaning in like queries of postgres this was resulting in undesired results
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withReference(ServicePaymentReference.of("percent%ref"))
                .insert();

        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withReference(ServicePaymentReference.of("percentref"))
                .insert();

        ChargeSearchParams params = new ChargeSearchParams()
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withReferenceLike(ServicePaymentReference.of("percent%"));

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(params);

        // then
        assertThat(charges.size(), is(1));

        ChargeEntity charge = charges.get(0);
        assertThat(charge.getReference(), is(ServicePaymentReference.of("percent%ref")));
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

        ChargeSearchParams params = new ChargeSearchParams()
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withReferenceLike(ServicePaymentReference.of("cASe-insEnsiTIve"))
                .withEmailLike("EMAIL-ID@mail.com");

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(params);

        // then
        assertThat(charges.size(), is(2));

        ChargeEntity charge = charges.get(0);
        assertThat(charge.getReference(), is(ServicePaymentReference.of("Case-inSENSITIVE-Ref")));
        assertThat(charge.getEmail(), is("EMAIL-ID@MAIL.COM"));

        charge = charges.get(1);
        assertThat(charge.getReference(), is(ServicePaymentReference.of("case-Insensitive-ref")));
        assertThat(charge.getEmail(), is("email-id@mail.com"));
    }

    @Test
    public void aBasicTestAgainstSqlInjection() throws Exception {
        // given
        insertTestCharge();
        ChargeSearchParams params = new ChargeSearchParams()
                .withReferenceLike(ServicePaymentReference.of("reference"));
        // when passed in a simple reference string
        List<ChargeEntity> charges = chargeDao.findAllBy(params);
        // then it fetches a single result
        assertThat(charges.size(), is(1));

        // when passed in a non existent reference with an sql injected string
        ServicePaymentReference sqlInjectionReferenceString = ServicePaymentReference.of("reffff%' or 1=1 or c.reference like '%1");
        params = new ChargeSearchParams()
                .withReferenceLike(sqlInjectionReferenceString);
        charges = chargeDao.findAllBy(params);
        // then it fetches no result
        // with a typical sql injection vulnerable query doing this should fetch all results
        assertThat(charges.size(), is(0));
    }


    @Test
    public void searchChargeByReferenceAndLegacyStatusOnly() throws Exception {
        // given
        insertTestCharge();
        ChargeSearchParams params = new ChargeSearchParams()
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withReferenceLike(defaultTestCharge.getReference())
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
    public void searchChargeByReferenceAndStatusAndFromDateAndToDate() throws Exception {

        // given
        insertTestCharge();
        ChargeSearchParams params = new ChargeSearchParams()
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withReferenceLike(defaultTestCharge.getReference())
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
    public void searchChargeByReferenceAndStatusAndFromDate() throws Exception {

        // given
        insertTestCharge();
        ChargeSearchParams params = new ChargeSearchParams()
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withReferenceLike(defaultTestCharge.getReference())
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

        ChargeSearchParams params = new ChargeSearchParams()
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withReferenceLike(defaultTestCharge.getReference())
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

        ChargeSearchParams params = new ChargeSearchParams()
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
    public void searchChargeByReferenceAndStatusAndEmailAndCardBrandAndFromDateAndToDate() throws Exception {
        // given
        insertTestCharge();
        ChargeSearchParams params = new ChargeSearchParams()
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withReferenceLike(defaultTestCharge.getReference())
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
    public void searchChargeByReferenceAndStatusAndToDate() throws Exception {

        // given
        insertTestCharge();
        ChargeSearchParams params = new ChargeSearchParams()
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withReferenceLike(defaultTestCharge.getReference())
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
    public void searchChargeByReferenceAndStatusAndFromDate_ShouldReturnZeroIfDateIsNotInRange() throws Exception {
        insertTestCharge();

        ChargeSearchParams params = new ChargeSearchParams()
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withReferenceLike(defaultTestCharge.getReference())
                .withExternalState(EXTERNAL_CREATED.getStatus())
                .withFromDate(ZonedDateTime.parse(TO_DATE));

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(params);

        assertThat(charges.size(), is(0));
    }

    @Test
    public void searchChargeByReferenceAndStatusAndToDate_ShouldReturnZeroIfToDateIsNotInRange() throws Exception {
        insertTestCharge();
        ChargeSearchParams params = new ChargeSearchParams()
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withReferenceLike(defaultTestCharge.getReference())
                .withExternalState(EXTERNAL_CREATED.getStatus())
                .withToDate(ZonedDateTime.parse(FROM_DATE));

        List<ChargeEntity> charges = chargeDao.findAllBy(params);

        assertThat(charges.size(), is(0));
    }

    private void assertDateMatch(String createdDateString) {
        assertDateMatch(DateTimeUtils.toUTCZonedDateTime(createdDateString).get());
    }

    private void assertDateMatch(ZonedDateTime createdDateTime) {
        assertThat(createdDateTime, within(1, ChronoUnit.MINUTES, now()));
    }

    @Test
    public void chargeEvents_shouldRecordTransactionIdWithEachStatusChange() throws Exception {
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
    public void invalidSizeOfReference() throws Exception {
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
        // Ensure always max precision is being millis
        assertThat(chargeEntity.getCreatedDate().getNano() % 1000000, is(0));
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
    public void findById_shouldFindChargeEntity() throws Exception {

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
    public void testFindByDate_status_findsValidChargeForStatus() throws Exception {
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(100L)
                .withExternalChargeId("ext-id")
                .withCreatedDate(now().minusHours(2))
                .insert();

        ArrayList<ChargeStatus> chargeStatuses = Lists.newArrayList(CREATED, ENTERING_CARD_DETAILS, AUTHORISATION_SUCCESS);

        List<ChargeEntity> charges = chargeDao.findBeforeDateWithStatusIn(now().minusHours(1), chargeStatuses);

        assertThat(charges.size(), is(1));
        assertEquals(charges.get(0).getId(), new Long(100));
    }

    @Test
    public void testFindByDateStatus_findsNoneForValidStatus() throws Exception {
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(100L)
                .withExternalChargeId("ext-id")
                .withCreatedDate(now().minusHours(2))
                .insert();

        ArrayList<ChargeStatus> chargeStatuses = Lists.newArrayList(CAPTURE_READY, SYSTEM_CANCELLED);

        List<ChargeEntity> charges = chargeDao.findBeforeDateWithStatusIn(now().minusHours(1), chargeStatuses);

        assertThat(charges.size(), is(0));
    }

    @Test
    public void testFindByDateStatus_findsNoneForExpiredDate() throws Exception {
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(100L)
                .withExternalChargeId("ext-id")
                .withCreatedDate(now().minusMinutes(30))
                .insert();

        ArrayList<ChargeStatus> chargeStatuses = Lists.newArrayList(CREATED, ENTERING_CARD_DETAILS, AUTHORISATION_SUCCESS);

        List<ChargeEntity> charges = chargeDao.findBeforeDateWithStatusIn(now().minusHours(1), chargeStatuses);

        assertThat(charges.size(), is(0));
    }

    @Test
    public void findByAccountBetweenDatesWithStatusIn_findsChargeWithMatchingAccountAndStatusInsideRange() {
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(100L)
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
        assertThat(charges.get(0).getId(), is(100L));
    }

    @Test
    public void findByAccountBetweenDatesWithStatusIn_findsNoneWithNonMatchingAccountButMatchingStatusInsideRange() {
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(100L)
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
                .withChargeId(100L)
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
                .withChargeId(100L)
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
                .withChargeId(100L)
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
    public void testFindChargeByTokenId() {
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(100L)
                .withExternalChargeId("ext-id")
                .withCreatedDate(now())
                .withAmount(300L)
                .insert();

        databaseTestHelper.addToken(100L, "some-token-id");

        Optional<ChargeEntity> chargeOpt = chargeDao.findByTokenId("some-token-id");
        assertTrue(chargeOpt.isPresent());
        assertEquals(chargeOpt.get().getExternalId(), "ext-id");

        assertThat(chargeOpt.get().getGatewayAccount(), is(notNullValue()));
        assertThat(chargeOpt.get().getGatewayAccount().getId(), is(defaultTestAccount.getAccountId()));
    }

    @Test
    public void findChargesForCapture_shouldReturnChargesInCaptureApprovedState() throws Exception {
        final long chargeId1 = 101L;

        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(chargeId1)
                .withExternalChargeId("ext-id1")
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
                .withChargeId(102L)
                .withExternalChargeId("ext-id2")
                .withCreatedDate(now().minusHours(2))
                .withChargeStatus(CAPTURE_READY)
                .insert();

        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(103L)
                .withExternalChargeId("ext-id3")
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
        assertEquals(charges.get(0).getId(), new Long(101));
    }

    @Test
    public void findChargesForCapture_shouldNotReturnAChargeForWhichCaptureHasBeenAttemptedRecently() throws Exception {
        final long chargeId1 = 101L;
        final long chargeId2 = 102L;

        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(chargeId1)
                .withExternalChargeId("ext-id1")
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
                .withExternalChargeId("ext-id2")
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
    public void countChargesForCapture_shouldReturnNumberOfChargesInCaptureApprovedState() throws Exception {
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(101L)
                .withExternalChargeId("ext-id1")
                .withCreatedDate(now().minusHours(2))
                .withChargeStatus(CAPTURE_APPROVED)
                .insert();
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(102L)
                .withExternalChargeId("ext-id2")
                .withCreatedDate(now().minusHours(2))
                .withChargeStatus(CAPTURE_APPROVED_RETRY)
                .insert();
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(103L)
                .withExternalChargeId("ext-id3")
                .withCreatedDate(now())
                .withChargeStatus(CAPTURE_APPROVED_RETRY)
                .insert();
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestChargeEvent()
                .withChargeId(103L)
                .withDate(now())
                .withChargeStatus(CAPTURE_APPROVED_RETRY)
                .insert();

        assertThat(chargeDao.countChargesForCapture(Duration.ofHours(1)), is(2));
        assertThat(chargeDao.countChargesAwaitingCaptureRetry(Duration.ofHours(1)), is(1));
    }

    @Test
    public void countCaptureRetriesForCharge_shouldReturnNumberOfRetries() throws Exception {
        long chargeId = 101L;

        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(chargeId)
                .withExternalChargeId("ext-id1")
                .withCreatedDate(now().minusHours(2))
                .withChargeStatus(CAPTURE_APPROVED)
                .insert();

        assertThat(chargeDao.countCaptureRetriesForCharge(chargeId), is(0));

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

        assertThat(chargeDao.countCaptureRetriesForCharge(chargeId), is(2));

    }

    @Test
    public void findByIdAndLimit(){
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(101L)
                .withExternalChargeId("ext-id1")
                .withCreatedDate(now().minusHours(2))
                .withChargeStatus(CAPTURE_APPROVED)
                .insert();
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(102L)
                .withExternalChargeId("ext-id2")
                .withCreatedDate(now().minusHours(2))
                .withChargeStatus(CAPTURE_APPROVED_RETRY)
                .insert();

        assertThat(chargeDao.findByIdAndLimit(0L, 2).size(), is(2));
    }

    private void insertTestAccount() {
        this.defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
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
