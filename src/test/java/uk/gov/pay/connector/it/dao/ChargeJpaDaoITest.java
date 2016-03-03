package uk.gov.pay.connector.it.dao;

import com.google.common.collect.ImmutableMap;
import org.exparity.hamcrest.date.LocalDateTimeMatchers;
import org.exparity.hamcrest.date.ZonedDateTimeMatchers;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.MatcherAssert;
import org.hamcrest.TypeSafeMatcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import uk.gov.pay.connector.dao.ChargeJpaDao;
import uk.gov.pay.connector.dao.ChargeSearchQuery;
import uk.gov.pay.connector.dao.EventJpaDao;
import uk.gov.pay.connector.dao.PayDBIException;
import uk.gov.pay.connector.model.api.ExternalChargeStatus;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeEvent;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.util.DatabaseTestHelper;
import uk.gov.pay.connector.util.DateTimeUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertEquals;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.util.TransactionId.randomId;

public class ChargeJpaDaoITest {

    private static final Long GATEWAY_ACCOUNT_ID = 564532435L;
    private static final String RETURN_URL = "http://service.com/success-page";
    private static final String REFERENCE = "Test reference";
    private static final String FROM_DATE = "2016-01-01T01:00:00Z";
    private static final String TO_DATE = "2026-01-08T01:00:00Z";
    private static final String DESCRIPTION = "Test description";
    private static final Long AMOUNT = 101L;
    private static final Long CHARGE_ID = 977L;
    private static final String PAYMENT_PROVIDER = "test_provider";
    private static final String COUNCIL_TAX_PAYMENT_REFERENCE = "Council Tax Payment reference";

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    private ChargeJpaDao chargeDao;
    private EventJpaDao eventDao;
    public GuicedTestEnvironment env;
    private DatabaseTestHelper databaseTestHelper;

    @Before
    public void setUp() throws Exception {
        env = GuicedTestEnvironment.from(app.getPersistModule())
                .start();

        chargeDao = env.getInstance(ChargeJpaDao.class);
        eventDao = env.getInstance(EventJpaDao.class);

        databaseTestHelper = app.getDatabaseTestHelper();

        databaseTestHelper.addGatewayAccount(GATEWAY_ACCOUNT_ID.toString(), PAYMENT_PROVIDER);
        databaseTestHelper.addCharge(String.valueOf(CHARGE_ID), String.valueOf(GATEWAY_ACCOUNT_ID), AMOUNT, CREATED, RETURN_URL, "", REFERENCE, ZonedDateTime.now());
    }

    @After
    public void tearDown() {
        env.stop();
    }

    @Test
    public void searchChargesByGatewayAccountIdOnly() throws Exception {

        // given
        ChargeSearchQuery queryBuilder = new ChargeSearchQuery(GATEWAY_ACCOUNT_ID);

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(queryBuilder);

        // then
        assertThat(charges.size(), is(1));

        ChargeEntity charge = charges.get(0);
        assertThat(charge.getId(), is(CHARGE_ID));
        assertThat(charge.getAmount(), is(AMOUNT));
        assertThat(charge.getReference(), is(REFERENCE));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getStatus(), is(CREATED.getValue()));

        assertDateMatch(charge.getCreatedDate().toString());
    }

    @Test
    public void searchChargesByFullReferenceOnly() throws Exception {

        // given
        ChargeSearchQuery queryBuilder = new ChargeSearchQuery(GATEWAY_ACCOUNT_ID)
                .withReferenceLike(REFERENCE);

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(queryBuilder);

        // then
        assertThat(charges.size(), is(1));

        ChargeEntity charge = charges.get(0);
        assertThat(charge.getId(), is(CHARGE_ID));
        assertThat(charge.getAmount(), is(AMOUNT));
        assertThat(charge.getReference(), is(REFERENCE));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getStatus(), is(CREATED.getValue()));

        assertDateMatch(charge.getCreatedDate().toString());
    }

    @Test
    public void searchChargesByPartialReferenceOnly() throws Exception {

        // given
        String paymentReference = "Council Tax Payment reference 2";
        Long chargeId = System.currentTimeMillis();
        databaseTestHelper.addCharge(chargeId.toString(), GATEWAY_ACCOUNT_ID.toString(), AMOUNT, CREATED, RETURN_URL, UUID.randomUUID().toString(), paymentReference, ZonedDateTime.now());

        ChargeSearchQuery queryBuilder = new ChargeSearchQuery(GATEWAY_ACCOUNT_ID)
                .withReferenceLike("reference");

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(queryBuilder);

        // then
        assertThat(charges.size(), is(2));
        assertThat(charges.get(1).getId(), is(CHARGE_ID));
        assertThat(charges.get(1).getReference(), is(REFERENCE));
        assertThat(charges.get(0).getReference(), is(paymentReference));

        for (ChargeEntity charge : charges) {
            assertThat(charge.getAmount(), is(AMOUNT));
            assertThat(charge.getDescription(), is(DESCRIPTION));
            assertThat(charge.getStatus(), is(CREATED.getValue()));
            assertDateMatch(charge.getCreatedDate().toString());
        }
    }

    @Test
    public void searchChargeByReferenceAndStatusOnly() throws Exception {

        // given
        ChargeSearchQuery queryBuilder = new ChargeSearchQuery(GATEWAY_ACCOUNT_ID)
                .withReferenceLike(REFERENCE)
                .withStatusIn(CREATED);

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(queryBuilder);

        // then
        assertThat(charges.size(), is(1));
        ChargeEntity charge = charges.get(0);

        assertThat(charge.getId(), is(CHARGE_ID));
        assertThat(charge.getAmount(), is(AMOUNT));
        assertThat(charge.getReference(), is(REFERENCE));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getStatus(), is(CREATED.getValue()));
        assertDateMatch(charge.getCreatedDate().toString());
    }

    @Test
    public void searchChargeByReferenceAndStatusAndFromDateAndToDate() throws Exception {

        // given
        ChargeSearchQuery queryBuilder = new ChargeSearchQuery(GATEWAY_ACCOUNT_ID)
                .withReferenceLike(REFERENCE)
                .withStatusIn(CREATED)
                .withCreatedDateFrom(ZonedDateTime.parse(FROM_DATE))
                .withCreatedDateTo(ZonedDateTime.parse(TO_DATE));

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(queryBuilder);

        // then
        assertThat(charges.size(), is(1));
        ChargeEntity charge = charges.get(0);

        assertThat(charge.getId(), is(CHARGE_ID));
        assertThat(charge.getAmount(), is(AMOUNT));
        assertThat(charge.getReference(), is(REFERENCE));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getStatus(), is(CREATED.getValue()));
        assertDateMatch(charge.getCreatedDate().toString());
    }

    @Test
    public void searchChargeByReferenceAndStatusAndFromDate() throws Exception {

        // given
        ChargeSearchQuery queryBuilder = new ChargeSearchQuery(GATEWAY_ACCOUNT_ID)
                .withReferenceLike(REFERENCE)
                .withStatusIn(CREATED)
                .withCreatedDateFrom(ZonedDateTime.parse(FROM_DATE));

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(queryBuilder);

        // then
        assertThat(charges.size(), is(1));
        ChargeEntity charge = charges.get(0);

        assertThat(charge.getId(), is(CHARGE_ID));
        assertThat(charge.getAmount(), is(AMOUNT));
        assertThat(charge.getReference(), is(REFERENCE));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getStatus(), is(CREATED.getValue()));

        assertDateMatch(charge.getCreatedDate().toString());
    }

    @Test
    public void searchChargeByMultipleStatuses() {

        // given
        Long chargeId = System.currentTimeMillis();
        databaseTestHelper.addCharge(chargeId.toString(), GATEWAY_ACCOUNT_ID.toString(), AMOUNT, ENTERING_CARD_DETAILS, RETURN_URL, UUID.randomUUID().toString(), REFERENCE, ZonedDateTime.now());

        ChargeSearchQuery queryBuilder = new ChargeSearchQuery(GATEWAY_ACCOUNT_ID)
                .withReferenceLike(REFERENCE)
                .withStatusIn(CREATED, ENTERING_CARD_DETAILS)
                .withCreatedDateFrom(ZonedDateTime.parse(FROM_DATE));

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(queryBuilder);

        // then
        assertThat(charges.size(), is(2));
        assertThat(charges.get(0).getStatus(), is(ENTERING_CARD_DETAILS.getValue()));
        assertThat(charges.get(1).getStatus(), is(CREATED.getValue()));
    }

    @Test
    public void searchChargeByReferenceAndStatusAndToDate() throws Exception {

        // given
        ChargeSearchQuery queryBuilder = new ChargeSearchQuery(GATEWAY_ACCOUNT_ID)
                .withReferenceLike(REFERENCE)
                .withStatusIn(CREATED)
                .withCreatedDateTo(ZonedDateTime.parse(TO_DATE));

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(queryBuilder);

        // then
        assertThat(charges.size(), is(1));
        ChargeEntity charge = charges.get(0);

        assertThat(charge.getId(), is(CHARGE_ID));
        assertThat(charge.getAmount(), is(AMOUNT));
        assertThat(charge.getReference(), is(REFERENCE));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getStatus(), is(CREATED.getValue()));
        assertDateMatch(charge.getCreatedDate().toString());
    }

    @Test
    public void searchChargeByReferenceAndStatusAndFromDate_ShouldReturnZeroIfDateIsNotInRange() throws Exception {

        ChargeSearchQuery queryBuilder = new ChargeSearchQuery(GATEWAY_ACCOUNT_ID)
                .withReferenceLike(REFERENCE)
                .withStatusIn(CREATED)
                .withCreatedDateFrom(ZonedDateTime.parse(TO_DATE));

        List<ChargeEntity> charges = chargeDao.findAllBy(queryBuilder);

        assertThat(charges.size(), is(0));
    }

    @Test
    public void searchChargeByReferenceAndStatusAndToDate_ShouldReturnZeroIfToDateIsNotInRange() throws Exception {

        ChargeSearchQuery queryBuilder = new ChargeSearchQuery(GATEWAY_ACCOUNT_ID)
                .withReferenceLike(REFERENCE)
                .withStatusIn(CREATED)
                .withCreatedDateTo(ZonedDateTime.parse(FROM_DATE));

        List<ChargeEntity> charges = chargeDao.findAllBy(queryBuilder);

        assertThat(charges.size(), is(0));
    }

    @Test
    public void insertAmountAndThenGetAmountById() throws Exception {

        // given
        Long id = System.currentTimeMillis();
        databaseTestHelper.addCharge(id.toString(),
                GATEWAY_ACCOUNT_ID.toString(), AMOUNT, CREATED, RETURN_URL, UUID.randomUUID().toString(), REFERENCE, ZonedDateTime.now());

        // when
        ChargeEntity charge = chargeDao.findById(id).get();

        // then
        assertThat(charge.getId(), is(id));
        assertThat(charge.getAmount(), is(AMOUNT));
        assertThat(charge.getReference(), is(REFERENCE));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getStatus(), is(CREATED.getValue()));
        assertThat(charge.getGatewayAccount().getId(), is(GATEWAY_ACCOUNT_ID));
        assertThat(charge.getReturnUrl(), is(RETURN_URL));
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime createdDate = charge.getCreatedDate();
        assertThat(createdDate, ZonedDateTimeMatchers.isDayOfMonth(now.getDayOfMonth()));
        assertThat(createdDate, ZonedDateTimeMatchers.isMonth(now.getMonth()));
        assertThat(createdDate, ZonedDateTimeMatchers.isYear(now.getYear()));
        MatcherAssert.assertThat(createdDate, ZonedDateTimeMatchers.within(1, ChronoUnit.MINUTES, now));
    }

    @Test
    public void updateStatusToEnteringCardDetailsFromCreated_shouldReturnOne() throws Exception {

        List<ChargeStatus> oldStatuses = newArrayList(CREATED, ENTERING_CARD_DETAILS);

        chargeDao.updateNewStatusWhereOldStatusIn(CHARGE_ID, ENTERING_CARD_DETAILS, oldStatuses);

        ChargeEntity charge = chargeDao.findById(CHARGE_ID).get();

        assertThat(charge.getId(), is(CHARGE_ID));
        assertThat(charge.getStatus(), is(ENTERING_CARD_DETAILS.getValue()));
        assertLoggedEvents(CHARGE_ID, ENTERING_CARD_DETAILS);
    }

    @Test
    public void throwDBIExceptionIfStatusNotUpdateForMissingCharge() throws Exception {
        String unknownId = "128457938450746";
        ChargeStatus status = AUTHORISATION_SUCCESS;

        expectedEx.expect(PayDBIException.class);
        expectedEx.expectMessage("Could not update charge '" + unknownId + "' with status " + status.toString());

        chargeDao.updateStatus(Long.valueOf(unknownId), status);
    }

    @Test
    public void throwDBIExceptionIfStatusNotUpdateForMissingCharge_old() throws Exception {
        String unknownId = "128457938450746";
        ChargeStatus status = AUTHORISATION_SUCCESS;

        expectedEx.expect(PayDBIException.class);
        expectedEx.expectMessage("Could not update charge '" + unknownId + "' with status " + status.toString());

        chargeDao.updateStatus(unknownId, status);
    }

    private void assertLoggedEvents(Long chargeId, ChargeStatus... statuses) {
        List<ChargeEvent> events = eventDao.findEvents(GATEWAY_ACCOUNT_ID, chargeId);
        assertThat(events, shouldIncludeStatus(statuses));
    }

    private void assertLoggedEvents(ChargeStatus... statuses) {
        List<ChargeEvent> events = eventDao.findEvents(GATEWAY_ACCOUNT_ID, CHARGE_ID);
        assertThat(events, shouldIncludeStatus(statuses));
    }

    private Matcher<? super List<ChargeEvent>> shouldIncludeStatus(ChargeStatus... expectedStatuses) {
        return new TypeSafeMatcher<List<ChargeEvent>>() {
            @Override
            protected boolean matchesSafely(List<ChargeEvent> chargeEvents) {
                List<ChargeStatus> actualStatuses = chargeEvents.stream().map(ce -> ce.getStatus()).collect(toList());
                return actualStatuses.containsAll(asList(expectedStatuses));
            }

            @Override
            public void describeTo(Description description) {
                description.appendText(String.format("does not contain [%s]", expectedStatuses));
            }
        };
    }

    @Deprecated
    private ImmutableMap<String, Object> newCharge(long amount, String reference) {
        return ImmutableMap.of(
                "amount", amount,
                "reference", reference,
                "description", DESCRIPTION,
                "return_url", RETURN_URL);
    }

    private void assertDateMatch(String createdDateString) {
        ZonedDateTime createdDateTime = DateTimeUtils.toUTCZonedDateTime(createdDateString).get();
        assertThat(createdDateTime, ZonedDateTimeMatchers.within(1, ChronoUnit.MINUTES, ZonedDateTime.now()));
    }

    @Test
    public void shouldReturnChargeByTransactionId() {

        Long id = System.currentTimeMillis();
        String transactionId = UUID.randomUUID().toString();

        databaseTestHelper.addCharge(id.toString(),
                GATEWAY_ACCOUNT_ID.toString(), AMOUNT, CREATED, RETURN_URL, transactionId, "Whatever Payment", ZonedDateTime.now());

        Optional<String> account = chargeDao.findAccountByTransactionId(PAYMENT_PROVIDER, transactionId);

        assertThat(account.isPresent(), is(true));
        assertThat(account.get(), is(String.valueOf(GATEWAY_ACCOUNT_ID)));
    }

    @Test
    public void insertAmountAndThenGetAmountById_old() throws Exception {
        Map<String, Object> charge = chargeDao.findById(CHARGE_ID.toString()).get();

        assertThat(charge.get("charge_id"), is(String.valueOf(CHARGE_ID)));
        assertThat(charge.get("amount"), is(AMOUNT));
        assertThat(charge.get("reference"), is(REFERENCE));
        assertThat(charge.get("description"), is(DESCRIPTION));
        assertThat(charge.get("status"), is(CREATED.getValue()));
        assertThat(charge.get("gateway_account_id"), is(String.valueOf(GATEWAY_ACCOUNT_ID)));
        assertThat(charge.get("return_url"), is(RETURN_URL));
        LocalDateTime createdDate = ((ZonedDateTime) charge.get("created_date")).toLocalDateTime();
        LocalDateTime today = LocalDateTime.now();
        MatcherAssert.assertThat(createdDate, LocalDateTimeMatchers.sameDay(today));
        MatcherAssert.assertThat(createdDate, LocalDateTimeMatchers.sameMonthOfYear(today));
        MatcherAssert.assertThat(createdDate, LocalDateTimeMatchers.sameYear(today));
        MatcherAssert.assertThat(createdDate, LocalDateTimeMatchers.within(1, ChronoUnit.MINUTES, today));
    }

    @Test
    public void searchChargesByFullReferenceOnly_old() throws Exception {
        List<Map<String, Object>> charges = chargeDao.findAllBy(GATEWAY_ACCOUNT_ID.toString(), REFERENCE, null, null, null);
        assertThat(charges.size(), is(1));
        Map<String, Object> charge = charges.get(0);

        assertThat(charge.get("charge_id"), is(String.valueOf(CHARGE_ID)));
        assertThat(charge.get("amount"), is(AMOUNT));
        assertThat(charge.get("reference"), is(REFERENCE));
        assertThat(charge.get("description"), is(DESCRIPTION));
        assertThat(charge.get("status"), is(CREATED.getValue()));

        assertDateMatch(charge.get("created_date").toString());
    }

    @Test
    public void searchChargesByPartialReferenceOnly_old() throws Exception {

        Long id = System.currentTimeMillis();
        databaseTestHelper.addCharge(id.toString(),
                GATEWAY_ACCOUNT_ID.toString(), AMOUNT, CREATED, RETURN_URL, UUID.randomUUID().toString(), COUNCIL_TAX_PAYMENT_REFERENCE, ZonedDateTime.now());

        List<Map<String, Object>> charges = chargeDao.findAllBy(GATEWAY_ACCOUNT_ID.toString(), "reference", null, null, null);
        assertThat(charges.size(), is(2));
        assertThat(charges.get(1).get("charge_id"), is(String.valueOf(CHARGE_ID)));
        assertThat(charges.get(1).get("reference"), is(REFERENCE));
        assertThat(charges.get(0).get("reference"), is(COUNCIL_TAX_PAYMENT_REFERENCE));

        for (Map<String, Object> charge : charges) {
            assertThat(charge.get("amount"), is(AMOUNT));
            assertThat(charge.get("description"), is(DESCRIPTION));
            assertThat(charge.get("status"), is(CREATED.getValue()));
            assertDateMatch(charge.get("created_date").toString());
        }
    }

    @Test
    public void searchChargeByReferenceAndStatusOnly_old() throws Exception {
        List<Map<String, Object>> charges = chargeDao.findAllBy(GATEWAY_ACCOUNT_ID.toString(), REFERENCE, ExternalChargeStatus.EXT_CREATED, null, null);
        assertThat(charges.size(), is(1));
        Map<String, Object> charge = charges.get(0);

        assertThat(charge.get("charge_id"), is(String.valueOf(CHARGE_ID)));
        assertThat(charge.get("amount"), is(AMOUNT));
        assertThat(charge.get("reference"), is(REFERENCE));
        assertThat(charge.get("description"), is(DESCRIPTION));
        assertThat(charge.get("status"), is(CREATED.getValue()));
        assertDateMatch(charge.get("created_date").toString());
    }

    @Test
    public void searchChargeByReferenceAndStatusAndFromDateAndToDate_old() throws Exception {
        List<Map<String, Object>> charges = chargeDao.findAllBy(GATEWAY_ACCOUNT_ID.toString(), REFERENCE, ExternalChargeStatus.EXT_CREATED, FROM_DATE, TO_DATE);
        assertThat(charges.size(), is(1));
        Map<String, Object> charge = charges.get(0);

        assertThat(charge.get("charge_id"), is(String.valueOf(CHARGE_ID)));
        assertThat(charge.get("amount"), is(AMOUNT));
        assertThat(charge.get("reference"), is(REFERENCE));
        assertThat(charge.get("description"), is(DESCRIPTION));
        assertThat(charge.get("status"), is(CREATED.getValue()));
        assertDateMatch(charge.get("created_date").toString());
    }

    @Test
    public void searchChargeByReferenceAndStatusAndFromDate_old() throws Exception {
        List<Map<String, Object>> charges = chargeDao.findAllBy(GATEWAY_ACCOUNT_ID.toString(), REFERENCE, ExternalChargeStatus.EXT_CREATED, FROM_DATE, null);
        assertThat(charges.size(), is(1));
        Map<String, Object> charge = charges.get(0);

        assertThat(charge.get("charge_id"), is(String.valueOf(CHARGE_ID)));
        assertThat(charge.get("amount"), is(AMOUNT));
        assertThat(charge.get("reference"), is(REFERENCE));
        assertThat(charge.get("description"), is(DESCRIPTION));
        assertThat(charge.get("status"), is(CREATED.getValue()));
        assertDateMatch(charge.get("created_date").toString());
    }

    @Test
    public void searchChargeByReferenceAndStatusAndToDate_old() throws Exception {
        List<Map<String, Object>> charges = chargeDao.findAllBy(GATEWAY_ACCOUNT_ID.toString(), REFERENCE, ExternalChargeStatus.EXT_CREATED, null, TO_DATE);
        assertThat(charges.size(), is(1));
        Map<String, Object> charge = charges.get(0);

        assertThat(charge.get("charge_id"), is(String.valueOf(CHARGE_ID)));
        assertThat(charge.get("amount"), is(AMOUNT));
        assertThat(charge.get("reference"), is(REFERENCE));
        assertThat(charge.get("description"), is(DESCRIPTION));
        assertThat(charge.get("status"), is(CREATED.getValue()));
        assertDateMatch(charge.get("created_date").toString());
    }

    @Test
    public void searchChargeByReferenceAndStatusAndFromDate_ShouldReturnZero_old() throws Exception {
        List<Map<String, Object>> charges = chargeDao.findAllBy(GATEWAY_ACCOUNT_ID.toString(), REFERENCE, ExternalChargeStatus.EXT_CREATED, TO_DATE, null);
        assertThat(charges.size(), is(0));
    }

    @Test
    public void searchChargeByReferenceAndStatusAndToDate_ShouldReturnZero_old() throws Exception {
        List<Map<String, Object>> charges = chargeDao.findAllBy(GATEWAY_ACCOUNT_ID.toString(), REFERENCE, ExternalChargeStatus.EXT_CREATED, null, FROM_DATE);
        assertThat(charges.size(), is(0));
    }

    @Test
    public void insertChargeAndThenUpdateStatus_old() throws Exception {
        chargeDao.updateStatus(CHARGE_ID, AUTHORISATION_READY);

        Map<String, Object> charge = chargeDao.findById(CHARGE_ID.toString()).get();

        assertThat(charge.get("status"), is(AUTHORISATION_READY.getValue()));

        assertLoggedEvents(AUTHORISATION_READY);
    }

    @Test
    public void insertChargeAndThenUpdateGatewayTransactionId_old() throws Exception {
        String transactionId = randomId();
        chargeDao.updateGatewayTransactionId(CHARGE_ID.toString(), transactionId);

        Map<String, Object> charge = chargeDao.findById(CHARGE_ID.toString()).get();
        assertThat(charge.get("gateway_transaction_id"), is(transactionId));
    }

    @Test
    public void insertChargeAndThenUpdateStatusPerGatewayTransactionId_old() throws Exception {
        String gatewayTransactionId = randomId();

        chargeDao.updateGatewayTransactionId(CHARGE_ID.toString(), gatewayTransactionId);
        chargeDao.updateStatusWithGatewayInfo(PAYMENT_PROVIDER, gatewayTransactionId, AUTHORISATION_READY);

        Map<String, Object> charge = chargeDao.findById(CHARGE_ID.toString()).get();

        assertThat(charge.get("gateway_transaction_id"), is(gatewayTransactionId));
        assertThat(charge.get("status"), is(AUTHORISATION_READY.getValue()));

        assertLoggedEvents(AUTHORISATION_READY);
    }

    @Test
    public void shouldThrowExceptionWhenUpdateStatusWithGatewayInfoIsNotFound() {

        String gatewayTransactionId = randomId();

        expectedEx.expect(PayDBIException.class);
        expectedEx.expectMessage(is("Could not update charge (gateway_transaction_id: " + gatewayTransactionId + ") with status AUTHORISATION SUBMITTED"));

        chargeDao.updateStatusWithGatewayInfo(PAYMENT_PROVIDER, gatewayTransactionId, AUTHORISATION_SUBMITTED);
    }

    @Test
    public void updateStatusToEnteringCardDetailsFromCreated_shouldReturnOne_old() throws Exception {
        List<ChargeStatus> oldStatuses = newArrayList(CREATED, ENTERING_CARD_DETAILS);
        int rowsUpdated = chargeDao.updateNewStatusWhereOldStatusIn(CHARGE_ID, ENTERING_CARD_DETAILS, oldStatuses);

        assertEquals(1, rowsUpdated);
        Map<String, Object> charge = chargeDao.findById(CHARGE_ID.toString()).get();
        assertThat(charge.get("charge_id"), is(String.valueOf(CHARGE_ID)));
        assertThat(charge.get("status"), is(ENTERING_CARD_DETAILS.getValue()));

        assertLoggedEvents(ENTERING_CARD_DETAILS);
    }

    @Test
    public void updateStatusToEnteringCardDetailsFromCaptured_shouldReturnZero_old() throws Exception {
        chargeDao.updateStatus(CHARGE_ID, CAPTURE_SUBMITTED);
        List<ChargeStatus> oldStatuses = newArrayList(CREATED, ENTERING_CARD_DETAILS);
        int rowsUpdated = chargeDao.updateNewStatusWhereOldStatusIn(CHARGE_ID, ENTERING_CARD_DETAILS, oldStatuses);

        assertEquals(0, rowsUpdated);
        Map<String, Object> charge = chargeDao.findById(CHARGE_ID.toString()).get();
        assertThat(charge.get("charge_id"), is(String.valueOf(CHARGE_ID)));
        assertThat(charge.get("status"), is(CAPTURE_SUBMITTED.getValue()));

        List<ChargeEvent> events = eventDao.findEvents(GATEWAY_ACCOUNT_ID, CHARGE_ID);
        assertThat(events, not(shouldIncludeStatus(ENTERING_CARD_DETAILS)));
    }

    @Test
    public void shouldUpdateEventsWhenMergeWithChargeEntityWithNewStatus() {

        Long chargeId = 56735L;
        String transactionId = "345654";
        databaseTestHelper.addCharge(String.valueOf(chargeId), String.valueOf(GATEWAY_ACCOUNT_ID), AMOUNT, CREATED, RETURN_URL, transactionId, REFERENCE, ZonedDateTime.now(ZoneId.of("UTC")));

        Optional<ChargeEntity> charge = chargeDao.findById(chargeId);
        ChargeEntity entity = charge.get();
        entity.setStatus(ENTERING_CARD_DETAILS);

        chargeDao.mergeChargeEntityWithChangedStatus(entity);

        List<ChargeEvent> events = eventDao.findEvents(GATEWAY_ACCOUNT_ID, chargeId);

        assertThat(events, hasSize(1));
        assertThat(events, shouldIncludeStatus(ENTERING_CARD_DETAILS));
    }

    @Test
    public void invalidSizeOfFields_old() throws Exception {
        expectedEx.expect(RuntimeException.class);
        Map<String, Object> chargeData = new HashMap<>(newCharge(AMOUNT, REFERENCE));
        chargeData.put("reference", randomAlphanumeric(512));
        chargeDao.saveNewCharge(GATEWAY_ACCOUNT_ID.toString(), chargeData);
    }

    @Test
    public void shouldFindChargeForAccount() {
        Map<String, Object> charge = chargeDao.findChargeForAccount(String.valueOf(CHARGE_ID), String.valueOf(GATEWAY_ACCOUNT_ID)).get();
        assertThat(charge.get("charge_id"), is(String.valueOf(CHARGE_ID)));
        assertThat(charge.get("amount"), is(AMOUNT));
        assertThat(charge.get("reference"), is(REFERENCE));
        assertThat(charge.get("payment_provider"), is(PAYMENT_PROVIDER));
        assertThat(charge.get("description"), is(DESCRIPTION));
        assertThat(charge.get("status"), is(CREATED.getValue()));
        assertThat(charge.get("gateway_account_id"), is(String.valueOf(GATEWAY_ACCOUNT_ID)));
        assertThat(charge.get("return_url"), is(RETURN_URL));
    }

    @Test
    public void shouldSaveANewCharge() {

        Map<String, Object> newCharge = new HashMap<>();
        newCharge.put("amount", "12345");
        newCharge.put("reference", "This is a reference");
        newCharge.put("description", "This is a description");
        newCharge.put("return_url", "http://return.com");

        String chargeId = chargeDao.saveNewCharge(String.valueOf(GATEWAY_ACCOUNT_ID), newCharge);

        assertThat(chargeId, is(notNullValue()));
        assertThat(Long.valueOf(chargeId), is(notNullValue()));
    }

    @Test
    public void shouldReturnNullFindingByIdWhenChargeDoesNotExist() {
        Optional<Map<String, Object>> charge = chargeDao.findById("5686541");

        assertThat(charge.isPresent(), is(false));
    }

    @Test
    public void shouldFindChargeEntityByProviderAndTransactionId() {

        // given
        String transactionId = "7826782163";
        ZonedDateTime createdDate = ZonedDateTime.now(ZoneId.of("UTC"));
        Long chargeId = 9999L;

        databaseTestHelper.addCharge(String.valueOf(chargeId), String.valueOf(GATEWAY_ACCOUNT_ID), AMOUNT, CREATED, RETURN_URL, transactionId, REFERENCE, createdDate);

        // when
        Optional<ChargeEntity> chargeOptional = chargeDao.findByProviderAndTransactionId(PAYMENT_PROVIDER, transactionId);

        // then
        assertThat(chargeOptional.isPresent(), is(true));

        ChargeEntity charge = chargeOptional.get();
        assertThat(charge.getId(), is(chargeId));
        assertThat(charge.getGatewayTransactionId(), is(transactionId));
        assertThat(charge.getReturnUrl(), is(RETURN_URL));
        assertThat(charge.getStatus(), is(CREATED.getValue()));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getCreatedDate(), is(createdDate));
        assertThat(charge.getReference(), is(REFERENCE));
        assertThat(charge.getGatewayAccount(), is(notNullValue()));
    }

    @Test
    public void shouldGetGatewayAccountWhenFindingChargeEntityByProviderAndTransactionId() {

        String transactionId = "7826782163";
        ZonedDateTime createdDate = ZonedDateTime.now();
        databaseTestHelper.addCharge(String.valueOf(8888L), String.valueOf(GATEWAY_ACCOUNT_ID), AMOUNT, CREATED, RETURN_URL, transactionId, REFERENCE, createdDate);

        Optional<ChargeEntity> chargeOptional = chargeDao.findByProviderAndTransactionId(PAYMENT_PROVIDER, transactionId);

        assertThat(chargeOptional.isPresent(), is(true));

        ChargeEntity charge = chargeOptional.get();
        GatewayAccountEntity gatewayAccount = charge.getGatewayAccount();
        assertThat(gatewayAccount, is(notNullValue()));
        assertThat(gatewayAccount.getId(), is(GATEWAY_ACCOUNT_ID));
        assertThat(gatewayAccount.getGatewayName(), is(PAYMENT_PROVIDER));
        assertThat(gatewayAccount.getCredentials(), is(Collections.EMPTY_MAP));
    }

    @Test
    public void shouldGetChargeByChargeIdWithCorrectAssociatedAccountId() {

        String transactionId = "7826782163";
        ZonedDateTime createdDate = ZonedDateTime.now(ZoneId.of("UTC"));
        Long chargeId = 876786L;

        databaseTestHelper.addCharge(String.valueOf(chargeId), String.valueOf(GATEWAY_ACCOUNT_ID), AMOUNT, CREATED, RETURN_URL, transactionId, REFERENCE, createdDate);

        ChargeEntity charge = chargeDao.findChargeForAccount(chargeId, String.valueOf(GATEWAY_ACCOUNT_ID)).get();

        assertThat(charge.getId(), is(chargeId));
        assertThat(charge.getGatewayTransactionId(), is(transactionId));
        assertThat(charge.getReturnUrl(), is(RETURN_URL));
        assertThat(charge.getStatus(), is(CREATED.getValue()));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getCreatedDate(), is(createdDate));
        assertThat(charge.getReference(), is(REFERENCE));
        assertThat(charge.getGatewayAccount(), is(notNullValue()));
        assertThat(charge.getGatewayAccount().getId(), is(GATEWAY_ACCOUNT_ID));
    }

    @Test
    public void shouldGetChargeByChargeIdAsNullWhenAccountIdDoesNotMatch() {
        Optional<ChargeEntity> chargeForAccount = chargeDao.findChargeForAccount(CHARGE_ID, String.valueOf(456781L));
        assertThat(chargeForAccount.isPresent(), is(false));
    }
}
