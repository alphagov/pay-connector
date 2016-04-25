package uk.gov.pay.connector.it.dao;

import com.google.common.collect.Lists;
import org.apache.commons.lang.RandomStringUtils;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.MatcherAssert;
import org.hamcrest.TypeSafeMatcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.ChargeSearch;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.util.DateTimeUtils;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static java.time.ZonedDateTime.now;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static junit.framework.TestCase.assertTrue;
import static org.exparity.hamcrest.date.ZonedDateTimeMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;
import static uk.gov.pay.connector.dao.ChargeSearch.aChargeSearch;
import static uk.gov.pay.connector.fixture.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.model.api.ExternalChargeStatus.EXT_CREATED;
import static uk.gov.pay.connector.model.api.ExternalChargeStatus.EXT_IN_PROGRESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class ChargeDaoITest {

    private static final String FROM_DATE = "2016-01-01T01:00:00Z";
    private static final String TO_DATE = "2026-01-08T01:00:00Z";
    private static final String DESCRIPTION = "Test description";

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    private ChargeDao chargeDao;
    public GuicedTestEnvironment env;

    private DatabaseFixtures.TestAccount defaultTestAccount;
    private DatabaseFixtures.TestCharge defaultTestCharge;

    @Before
    public void setUp() throws Exception {
        env = GuicedTestEnvironment.from(app.getPersistModule())
                .start();
        chargeDao = env.getInstance(ChargeDao.class);

        this.defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestAccount()
                .insert();

        this.defaultTestCharge = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .insert();
    }

    @After
    public void tearDown() {
        env.stop();
    }

    @Test
    public void searchChargesByGatewayAccountIdOnly() throws Exception {

        // given
        ChargeSearch queryBuilder = aChargeSearch(defaultTestAccount.getAccountId());

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(queryBuilder);

        // then
        assertThat(charges.size(), is(1));

        ChargeEntity charge = charges.get(0);
        assertThat(charge.getId(), is(defaultTestCharge.getChargeId()));
        assertThat(charge.getAmount(), is(defaultTestCharge.getAmount()));
        assertThat(charge.getReference(), is(defaultTestCharge.getReference()));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getStatus(), is(defaultTestCharge.getChargeStatus().toString()));

        assertDateMatch(charge.getCreatedDate().toString());
    }

    @Test
    public void searchChargesByFullReferenceOnly() throws Exception {

        // given
        ChargeSearch queryBuilder = aChargeSearch(defaultTestAccount.getAccountId())
                .withReferenceLike(defaultTestCharge.getReference());

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(queryBuilder);

        // then
        assertThat(charges.size(), is(1));

        ChargeEntity charge = charges.get(0);
        assertThat(charge.getId(), is(defaultTestCharge.getChargeId()));
        assertThat(charge.getAmount(), is(defaultTestCharge.getAmount()));
        assertThat(charge.getReference(), is(defaultTestCharge.getReference()));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getStatus(), is(defaultTestCharge.getChargeStatus().toString()));

        assertDateMatch(charge.getCreatedDate().toString());
    }

    @Test
    public void searchChargesByPartialReferenceOnly() throws Exception {

        // given
        String paymentReference = "Council Tax Payment reference 2";
        Long chargeId = System.currentTimeMillis();
        String externalChargeId = "chargeabc";

        DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withReference(paymentReference)
                .insert();

        ChargeSearch queryBuilder = aChargeSearch(defaultTestAccount.getAccountId())
                .withReferenceLike("reference");

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(queryBuilder);

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
        }
    }

    @Test
    public void searchChargeByReferenceAndStatusOnly() throws Exception {

        // given
        ChargeSearch queryBuilder = aChargeSearch(defaultTestAccount.getAccountId())
                .withReferenceLike(defaultTestCharge.getReference())
                .withExternalStatus(EXT_CREATED);

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(queryBuilder);

        // then
        assertThat(charges.size(), is(1));
        ChargeEntity charge = charges.get(0);

        assertThat(charge.getId(), is(defaultTestCharge.getChargeId()));
        assertThat(charge.getAmount(), is(defaultTestCharge.getAmount()));
        assertThat(charge.getReference(), is(defaultTestCharge.getReference()));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getStatus(), is(defaultTestCharge.getChargeStatus().toString()));
        assertDateMatch(charge.getCreatedDate().toString());
    }

    @Test
    public void searchChargeByReferenceAndStatusAndFromDateAndToDate() throws Exception {

        // given
        ChargeSearch queryBuilder = aChargeSearch(defaultTestAccount.getAccountId())
                .withReferenceLike(defaultTestCharge.getReference())
                .withExternalStatus(EXT_CREATED)
                .withCreatedDateFrom(ZonedDateTime.parse(FROM_DATE))
                .withCreatedDateTo(ZonedDateTime.parse(TO_DATE));

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(queryBuilder);

        // then
        assertThat(charges.size(), is(1));
        ChargeEntity charge = charges.get(0);

        assertThat(charge.getId(), is(defaultTestCharge.getChargeId()));
        assertThat(charge.getAmount(), is(defaultTestCharge.getAmount()));
        assertThat(charge.getReference(), is(defaultTestCharge.getReference()));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getStatus(), is(defaultTestCharge.getChargeStatus().toString()));
        assertDateMatch(charge.getCreatedDate().toString());
    }

    @Test
    public void searchChargeByReferenceAndStatusAndFromDate() throws Exception {

        // given
        ChargeSearch queryBuilder = aChargeSearch(defaultTestAccount.getAccountId())
                .withReferenceLike(defaultTestCharge.getReference())
                .withExternalStatus(EXT_CREATED)
                .withCreatedDateFrom(ZonedDateTime.parse(FROM_DATE));

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(queryBuilder);

        // then
        assertThat(charges.size(), is(1));
        ChargeEntity charge = charges.get(0);

        assertThat(charge.getId(), is(defaultTestCharge.getChargeId()));
        assertThat(charge.getAmount(), is(defaultTestCharge.getAmount()));
        assertThat(charge.getReference(), is(defaultTestCharge.getReference()));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getStatus(), is(defaultTestCharge.getChargeStatus().toString()));

        assertDateMatch(charge.getCreatedDate().toString());
    }

    @Test
    public void searchChargeByMultipleStatuses() {

        // given
        DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestCharge()
                .withChargeId(12345)
                .withTestAccount(defaultTestAccount)
                .withChargeStatus(ENTERING_CARD_DETAILS)
                .insert();

        DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestCharge()
                .withChargeId(12346)
                .withTestAccount(defaultTestAccount)
                .withChargeStatus(AUTHORISATION_READY)
                .insert();

        ChargeSearch queryBuilder = aChargeSearch(defaultTestAccount.getAccountId())
                .withReferenceLike(defaultTestCharge.getReference())
                .withExternalStatus(EXT_IN_PROGRESS)
                .withCreatedDateFrom(ZonedDateTime.parse(FROM_DATE));

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(queryBuilder);

        // then
        assertThat(charges.size(), is(2));
        assertThat(charges.get(0).getStatus(), is(AUTHORISATION_READY.getValue()));
        assertThat(charges.get(1).getStatus(), is(ENTERING_CARD_DETAILS.getValue()));
    }

    @Test
    public void searchChargesShouldBeOrderedByChargeIdDescending() {

        // given
        long accountId = 123;
        DatabaseFixtures.TestAccount account = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestAccount()
                .withAccountId(accountId)
                .insert();

        DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestCharge()
                .withChargeId(555L)
                .withTestAccount(account)
                .insert();

        DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestCharge()
                .withChargeId(557L)
                .withTestAccount(account)
                .insert();

        DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestCharge()
                .withChargeId(556L)
                .withTestAccount(account)
                .insert();

        ChargeSearch queryBuilder = aChargeSearch(accountId);

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(queryBuilder);

        // then
        assertThat(charges.size(), is(3));
        assertThat(charges.get(0).getId(), is(557L));
        assertThat(charges.get(1).getId(), is(556L));
        assertThat(charges.get(2).getId(), is(555L));
    }

    @Test
    public void searchChargeByReferenceAndStatusAndToDate() throws Exception {

        // given
        ChargeSearch queryBuilder = aChargeSearch(defaultTestAccount.getAccountId())
                .withReferenceLike(defaultTestCharge.getReference())
                .withExternalStatus(EXT_CREATED)
                .withCreatedDateTo(ZonedDateTime.parse(TO_DATE));

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(queryBuilder);

        // then
        assertThat(charges.size(), is(1));
        ChargeEntity charge = charges.get(0);

        assertThat(charge.getId(), is(defaultTestCharge.getChargeId()));
        assertThat(charge.getAmount(), is(defaultTestCharge.getAmount()));
        assertThat(charge.getReference(), is(defaultTestCharge.getReference()));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getStatus(), is(defaultTestCharge.getChargeStatus().toString()));
        assertDateMatch(charge.getCreatedDate().toString());
    }

    @Test
    public void searchChargeByReferenceAndStatusAndFromDate_ShouldReturnZeroIfDateIsNotInRange() throws Exception {

        ChargeSearch queryBuilder = aChargeSearch(defaultTestAccount.getAccountId())
                .withReferenceLike(defaultTestCharge.getReference())
                .withExternalStatus(EXT_CREATED)
                .withCreatedDateFrom(ZonedDateTime.parse(TO_DATE));

        List<ChargeEntity> charges = chargeDao.findAllBy(queryBuilder);

        assertThat(charges.size(), is(0));
    }

    @Test
    public void searchChargeByReferenceAndStatusAndToDate_ShouldReturnZeroIfToDateIsNotInRange() throws Exception {

        ChargeSearch queryBuilder = aChargeSearch(defaultTestAccount.getAccountId())
                .withReferenceLike(defaultTestCharge.getReference())
                .withExternalStatus(EXT_CREATED)
                .withCreatedDateTo(ZonedDateTime.parse(FROM_DATE));

        List<ChargeEntity> charges = chargeDao.findAllBy(queryBuilder);

        assertThat(charges.size(), is(0));
    }

    @Test
    public void insertAmountAndThenGetAmountById() throws Exception {

        // given
        Long chargeId = System.currentTimeMillis();
        String externalChargeId = "chargesfsdf";

        ZonedDateTime createdDateTime = ZonedDateTime.now(ZoneId.of("UTC"));
        DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withCreatedDate(createdDateTime)
                .insert();

        // when
        ChargeEntity charge = chargeDao.findById(chargeId).get();

        // then
        assertThat(charge.getId(), is(chargeId));
        assertThat(charge.getAmount(), is(defaultTestCharge.getAmount()));
        assertThat(charge.getReference(), is(defaultTestCharge.getReference()));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getStatus(), is(CREATED.getValue()));
        assertThat(charge.getGatewayAccount().getId(), is(defaultTestAccount.getAccountId()));
        assertThat(charge.getReturnUrl(), is(defaultTestCharge.getReturnUrl()));
        assertThat(charge.getCreatedDate(), is(createdDateTime));
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

    @Test
    public void shouldUpdateEventsWhenMergeWithChargeEntityWithNewStatus() {

        Long chargeId = 56735L;
        String externalChargeId = "charge456";

        String transactionId = "345654";

        DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withTransactionId(transactionId)
                .insert();

        Optional<ChargeEntity> charge = chargeDao.findById(chargeId);
        ChargeEntity entity = charge.get();
        entity.setStatus(ENTERING_CARD_DETAILS);

        chargeDao.mergeAndNotifyStatusHasChanged(entity);

        List<ChargeEventEntity> events = chargeDao.findById(chargeId).get().getEvents();

        assertThat(events, hasSize(1));
        assertThat(events, shouldIncludeStatus(ENTERING_CARD_DETAILS));
        assertDateMatch(events.get(0).getUpdated());
    }

    @Test
    public void invalidSizeOfReference() throws Exception {
        expectedEx.expect(RuntimeException.class);

        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity(defaultTestAccount.getPaymentProvider(), new HashMap<>());
        gatewayAccount.setId(defaultTestAccount.getAccountId());
        chargeDao.persist(aValidChargeEntity().withReference(RandomStringUtils.randomAlphanumeric(255)).build());
    }

    @Test
    public void shouldCreateANewCharge() {

        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity(defaultTestAccount.getPaymentProvider(), new HashMap<>());
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

        DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(chargeId)
                .withCreatedDate(createdDate)
                .withExternalChargeId(externalChargeId)
                .withTransactionId(transactionId)
                .insert();

        // when
        Optional<ChargeEntity> chargeOptional = chargeDao.findByProviderAndTransactionId(defaultTestAccount.getPaymentProvider(), transactionId);

        // then
        assertThat(chargeOptional.isPresent(), is(true));

        ChargeEntity charge = chargeOptional.get();
        assertThat(charge.getId(), is(chargeId));
        assertThat(charge.getGatewayTransactionId(), is(transactionId));
        assertThat(charge.getReturnUrl(), is(defaultTestCharge.getReturnUrl()));
        assertThat(charge.getStatus(), is(CREATED.getValue()));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getCreatedDate(), is(createdDate));
        assertThat(charge.getReference(), is(defaultTestCharge.getReference()));
        assertThat(charge.getGatewayAccount(), is(notNullValue()));
    }

    @Test
    public void shouldGetGatewayAccountWhenFindingChargeEntityByProviderAndTransactionId() {

        String transactionId = "7826782163";

        DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
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

        DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(chargeId)
                .withCreatedDate(createdDate)
                .withExternalChargeId(externalChargeId)
                .withTransactionId(transactionId)
                .insert();

        ChargeEntity charge = chargeDao.findByExternalIdAndGatewayAccount(externalChargeId, defaultTestAccount.getAccountId()).get();

        assertThat(charge.getId(), is(chargeId));
        assertThat(charge.getGatewayTransactionId(), is(transactionId));
        assertThat(charge.getReturnUrl(), is(defaultTestCharge.getReturnUrl()));
        assertThat(charge.getStatus(), is(defaultTestCharge.getChargeStatus().toString()));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getCreatedDate(), is(createdDate));
        assertThat(charge.getReference(), is(defaultTestCharge.getReference()));
        assertThat(charge.getGatewayAccount(), is(notNullValue()));
        assertThat(charge.getGatewayAccount().getId(), is(defaultTestAccount.getAccountId()));
    }

    @Test
    public void shouldGetChargeByChargeIdAsNullWhenAccountIdDoesNotMatch() {
        Optional<ChargeEntity> chargeForAccount = chargeDao.findByExternalIdAndGatewayAccount(defaultTestCharge.getExternalChargeId(), 456781L);
        assertThat(chargeForAccount.isPresent(), is(false));
    }

    @Test
    public void findByExternalId_shouldFindAChargeEntity() {
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
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(100L)
                .withExternalChargeId("ext-id")
                .withCreatedDate(now().minusHours(2))
                .insert();

        ArrayList<ChargeStatus> chargeStatuses = Lists.newArrayList(CREATED, ENTERING_CARD_DETAILS, AUTHORISATION_SUBMITTED, AUTHORISATION_SUCCESS);

        List<ChargeEntity> charges = chargeDao.findBeforeDateWithStatusIn(now().minusHours(1), chargeStatuses);

        assertThat(charges.size(), is(1));
        assertEquals(charges.get(0).getId(), new Long(100));
    }

    @Test
    public void testFindByDateStatus_findsNoneForValidStatus() throws Exception {
        DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
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
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(100L)
                .withExternalChargeId("ext-id")
                .withCreatedDate(now().minusMinutes(30))
                .insert();

        ArrayList<ChargeStatus> chargeStatuses = Lists.newArrayList(CREATED, ENTERING_CARD_DETAILS, AUTHORISATION_SUBMITTED, AUTHORISATION_SUCCESS);

        List<ChargeEntity> charges = chargeDao.findBeforeDateWithStatusIn(now().minusHours(1), chargeStatuses);

        assertThat(charges.size(), is(0));
    }

    @Test
    public void testFindChargeByTokenId() {
        DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(100L)
                .withExternalChargeId("ext-id")
                .withCreatedDate(now())
                .withAmount(300L)
                .insert();

        app.getDatabaseTestHelper().addToken(100L, "some-token-id");

        Optional<ChargeEntity> chargeOpt = chargeDao.findByTokenId("some-token-id");
        assertTrue(chargeOpt.isPresent());
        assertEquals(chargeOpt.get().getExternalId(), "ext-id");

        assertThat(chargeOpt.get().getGatewayAccount(), is(notNullValue()));
        assertThat(chargeOpt.get().getGatewayAccount().getId(), is(defaultTestAccount.getAccountId()));
    }
}
