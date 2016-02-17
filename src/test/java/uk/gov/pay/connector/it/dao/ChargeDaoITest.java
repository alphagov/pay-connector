package uk.gov.pay.connector.it.dao;

import com.google.common.collect.ImmutableMap;
import org.exparity.hamcrest.date.LocalDateTimeMatchers;
import org.exparity.hamcrest.date.ZonedDateTimeMatchers;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.MatcherAssert;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.EventDao;
import uk.gov.pay.connector.dao.PayDBIException;
import uk.gov.pay.connector.model.api.ExternalChargeStatus;
import uk.gov.pay.connector.model.domain.ChargeEvent;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.util.ChargeEventListener;
import uk.gov.pay.connector.util.DateTimeUtils;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.google.common.collect.Lists.newArrayList;
import static java.lang.Long.parseLong;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertEquals;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.util.TransactionId.randomId;

public class ChargeDaoITest {
    private static final String GATEWAY_ACCOUNT_ID = "564532435";
    private static final String RETURN_URL = "http://service.com/success-page";
    private static final String REFERENCE = "Test reference";
    private static final String FROM_DATE = "2016-01-01T01:00:00Z";
    private static final String TO_DATE = "2026-01-08T01:00:00Z";
    private static final String DESCRIPTION = "Test description";
    private static final long AMOUNT = 101;
    public static final String PAYMENT_PROVIDER = "test_provider";
    public static final String COUNCIL_TAX_PAYMENT_REFERENCE = "Council Tax Payment reference";

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    private ChargeDao chargeDao;
    private String chargeId;
    private EventDao eventDao;
    private ChargeEventListener eventListener;
    private DateTimeFormatter formatter;
    private String createdDate;

    @Before
    public void setUp() throws Exception {
        eventDao = new EventDao(app.getJdbi());
        eventListener = new ChargeEventListener(eventDao);
        chargeDao = new ChargeDao(app.getJdbi(), eventListener);
        app.getDatabaseTestHelper().addGatewayAccount(GATEWAY_ACCOUNT_ID, PAYMENT_PROVIDER);
        formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        chargeId = chargeDao.saveNewCharge(GATEWAY_ACCOUNT_ID, newCharge(AMOUNT, REFERENCE));
        createdDate = (String) chargeDao.findById(chargeId).get().get("created_date");
        chargeDao.saveNewCharge(GATEWAY_ACCOUNT_ID, newCharge(AMOUNT, COUNCIL_TAX_PAYMENT_REFERENCE));
    }

    @Test
    public void insertANewChargeAndReturnTheId() throws Exception {
        assertThat(chargeId, is("1"));
    }

    @Test
    public void insertAmountAndThenGetAmountById() throws Exception {
        Map<String, Object> charge = chargeDao.findById(chargeId).get();

        assertThat(charge.get("charge_id"), is(chargeId));
        assertThat(charge.get("amount"), is(AMOUNT));
        assertThat(charge.get("reference"), is(REFERENCE));
        assertThat(charge.get("description"), is(DESCRIPTION));
        assertThat(charge.get("status"), is(CREATED.getValue()));
        assertThat(charge.get("gateway_account_id"), is(GATEWAY_ACCOUNT_ID));
        assertThat(charge.get("return_url"), is(RETURN_URL));
        LocalDateTime createdDate = LocalDateTime.parse(charge.get("created_date").toString(), formatter);
        LocalDateTime today = LocalDateTime.now();
        MatcherAssert.assertThat(createdDate, LocalDateTimeMatchers.sameDay(today));
        MatcherAssert.assertThat(createdDate, LocalDateTimeMatchers.sameMonthOfYear(today));
        MatcherAssert.assertThat(createdDate, LocalDateTimeMatchers.sameYear(today));
        MatcherAssert.assertThat(createdDate, LocalDateTimeMatchers.within(1, ChronoUnit.MINUTES, today));
    }

    @Test
    public void searchChargesByFullReferenceOnly() throws Exception {
        List<Map<String, Object>> charges = chargeDao.findAllBy(GATEWAY_ACCOUNT_ID, REFERENCE, null, null, null);
        assertThat(charges.size(), is(1));
        Map<String, Object> charge = charges.get(0);

        assertThat(charge.get("charge_id"), is(chargeId));
        assertThat(charge.get("amount"), is(AMOUNT));
        assertThat(charge.get("reference"), is(REFERENCE));
        assertThat(charge.get("description"), is(DESCRIPTION));
        assertThat(charge.get("status"), is(CREATED.getValue()));

        assertDateMatch(charge.get("created_date").toString());
    }

    @Test
    public void searchChargesByPartialReferenceOnly() throws Exception {
        List<Map<String, Object>> charges = chargeDao.findAllBy(GATEWAY_ACCOUNT_ID, "reference", null, null, null);
        assertThat(charges.size(), is(2));
        assertThat(charges.get(1).get("charge_id"), is(chargeId));
        assertThat(charges.get(1).get("reference"), is(REFERENCE));
        assertThat(charges.get(0).get("reference"), is(COUNCIL_TAX_PAYMENT_REFERENCE));

        for(Map<String, Object> charge : charges) {
            assertThat(charge.get("amount"), is(AMOUNT));
            assertThat(charge.get("description"), is(DESCRIPTION));
            assertThat(charge.get("status"), is(CREATED.getValue()));
            assertDateMatch(charge.get("created_date").toString());
        }
    }

    @Test
    public void searchChargeByReferenceAndStatusOnly() throws Exception {
        List<Map<String, Object>> charges = chargeDao.findAllBy(GATEWAY_ACCOUNT_ID, REFERENCE, ExternalChargeStatus.EXT_CREATED, null, null);
        assertThat(charges.size(), is(1));
        Map<String, Object> charge = charges.get(0);

        assertThat(charge.get("charge_id"), is(chargeId));
        assertThat(charge.get("amount"), is(AMOUNT));
        assertThat(charge.get("reference"), is(REFERENCE));
        assertThat(charge.get("description"), is(DESCRIPTION));
        assertThat(charge.get("status"), is(CREATED.getValue()));
        assertDateMatch(charge.get("created_date").toString());
    }

    @Test
    public void searchChargeByReferenceAndStatusAndFromDateAndToDate() throws Exception {
        List<Map<String, Object>> charges = chargeDao.findAllBy(GATEWAY_ACCOUNT_ID, REFERENCE, ExternalChargeStatus.EXT_CREATED, FROM_DATE, TO_DATE);
        assertThat(charges.size(), is(1));
        Map<String, Object> charge = charges.get(0);

        assertThat(charge.get("charge_id"), is(chargeId));
        assertThat(charge.get("amount"), is(AMOUNT));
        assertThat(charge.get("reference"), is(REFERENCE));
        assertThat(charge.get("description"), is(DESCRIPTION));
        assertThat(charge.get("status"), is(CREATED.getValue()));
        assertDateMatch(charge.get("created_date").toString());
    }

    @Test
    public void searchChargeByReferenceAndStatusAndFromDate() throws Exception {
        List<Map<String, Object>> charges = chargeDao.findAllBy(GATEWAY_ACCOUNT_ID, REFERENCE, ExternalChargeStatus.EXT_CREATED, FROM_DATE, null);
        assertThat(charges.size(), is(1));
        Map<String, Object> charge = charges.get(0);

        assertThat(charge.get("charge_id"), is(chargeId));
        assertThat(charge.get("amount"), is(AMOUNT));
        assertThat(charge.get("reference"), is(REFERENCE));
        assertThat(charge.get("description"), is(DESCRIPTION));
        assertThat(charge.get("status"), is(CREATED.getValue()));
        assertDateMatch(charge.get("created_date").toString());
    }

    @Test
    public void findAccountByTransactionId_ShouldBeEmptyForNonExistentTransaction() throws Exception {
        Optional<String> accountByTransactionId = chargeDao.findAccountByTransactionId(PAYMENT_PROVIDER, "non-existent-tx-id");

        assertThat(accountByTransactionId, is(Optional.empty()));
    }

    @Test
    public void searchChargeByReferenceAndStatusAndToDate() throws Exception {
        List<Map<String, Object>> charges = chargeDao.findAllBy(GATEWAY_ACCOUNT_ID, REFERENCE, ExternalChargeStatus.EXT_CREATED, null, TO_DATE);
        assertThat(charges.size(), is(1));
        Map<String, Object> charge = charges.get(0);

        assertThat(charge.get("charge_id"), is(chargeId));
        assertThat(charge.get("amount"), is(AMOUNT));
        assertThat(charge.get("reference"), is(REFERENCE));
        assertThat(charge.get("description"), is(DESCRIPTION));
        assertThat(charge.get("status"), is(CREATED.getValue()));
        assertDateMatch(charge.get("created_date").toString());
    }

    @Test
    public void searchChargeByReferenceAndStatusAndFromDate_ShouldReturnZero() throws Exception {
        List<Map<String, Object>> charges = chargeDao.findAllBy(GATEWAY_ACCOUNT_ID, REFERENCE, ExternalChargeStatus.EXT_CREATED, TO_DATE, null);
        assertThat(charges.size(), is(0));
    }

    @Test
    public void searchChargeByReferenceAndStatusAndToDate_ShouldReturnZero() throws Exception {
        List<Map<String, Object>> charges = chargeDao.findAllBy(GATEWAY_ACCOUNT_ID, REFERENCE, ExternalChargeStatus.EXT_CREATED, null, FROM_DATE);
        assertThat(charges.size(), is(0));
    }

    @Test
    public void insertChargeAndThenUpdateStatus() throws Exception {
        chargeDao.updateStatus(chargeId, AUTHORISATION_SUBMITTED);

        Map<String, Object> charge = chargeDao.findById(chargeId).get();

        assertThat(charge.get("status"), is(AUTHORISATION_SUBMITTED.getValue()));

        assertLoggedEvents(AUTHORISATION_SUBMITTED);
    }

    @Test
    public void insertChargeAndThenUpdateGatewayTransactionId() throws Exception {
        String transactionId = randomId();
        chargeDao.updateGatewayTransactionId(chargeId, transactionId);

        Map<String, Object> charge = chargeDao.findById(chargeId).get();
        assertThat(charge.get("gateway_transaction_id"), is(transactionId));
    }

    @Test
    public void insertChargeAndThenUpdateStatusPerGatewayTransactionId() throws Exception {
        String gatewayTransactionId = randomId();

        chargeDao.updateGatewayTransactionId(chargeId, gatewayTransactionId);
        chargeDao.updateStatusWithGatewayInfo(PAYMENT_PROVIDER, gatewayTransactionId, AUTHORISATION_SUBMITTED);

        Map<String, Object> charge = chargeDao.findById(chargeId).get();
        assertThat(charge.get("status"), is(AUTHORISATION_SUBMITTED.getValue()));

        assertLoggedEvents(AUTHORISATION_SUBMITTED);
    }

    @Test
    public void updateStatusToEnteringCardDetailsFromCreated_shouldReturnOne() throws Exception {
        List<ChargeStatus> oldStatuses = newArrayList(CREATED, ENTERING_CARD_DETAILS);
        int rowsUpdated = chargeDao.updateNewStatusWhereOldStatusIn(chargeId, ENTERING_CARD_DETAILS, oldStatuses);

        assertEquals(1, rowsUpdated);
        Map<String, Object> charge = chargeDao.findById(chargeId).get();
        assertThat(charge.get("charge_id"), is(chargeId));
        assertThat(charge.get("status"), is(ENTERING_CARD_DETAILS.getValue()));

        assertLoggedEvents(CREATED, ENTERING_CARD_DETAILS);
    }

    @Test
    public void updateStatusToEnteringCardDetailsFromCaptured_shouldReturnZero() throws Exception {
        chargeDao.updateStatus(chargeId, CAPTURE_SUBMITTED);
        List<ChargeStatus> oldStatuses = newArrayList(CREATED, ENTERING_CARD_DETAILS);
        int rowsUpdated = chargeDao.updateNewStatusWhereOldStatusIn(chargeId, ENTERING_CARD_DETAILS, oldStatuses);

        assertEquals(0, rowsUpdated);
        Map<String, Object> charge = chargeDao.findById(chargeId).get();
        assertThat(charge.get("charge_id"), is(chargeId));
        assertThat(charge.get("status"), is(CAPTURE_SUBMITTED.getValue()));

        List<ChargeEvent> events = eventDao.findEvents(parseLong(GATEWAY_ACCOUNT_ID), parseLong(chargeId));
        assertThat(events, not(shouldIncludeStatus(ENTERING_CARD_DETAILS)));
    }

    @Test
    public void throwDBIExceptionIfStatusNotUpdateForMissingCharge() throws Exception {
        String unknownId = "128457938450746";
        ChargeStatus status = AUTHORISATION_SUCCESS;

        expectedEx.expect(PayDBIException.class);
        expectedEx.expectMessage("Could not update charge '" + unknownId + "' with status " + status.toString());

        chargeDao.updateStatus(unknownId, status);
    }

    @Test
    public void invalidSizeOfFields() throws Exception {
        expectedEx.expect(RuntimeException.class);
        Map<String, Object> chargeData = new HashMap<>(newCharge(AMOUNT, REFERENCE));
        chargeData.put("reference", randomAlphanumeric(512));
        chargeId = chargeDao.saveNewCharge(GATEWAY_ACCOUNT_ID, chargeData);
    }

    private void assertLoggedEvents(ChargeStatus... statuses) {
        List<ChargeEvent> events = eventDao.findEvents(parseLong(GATEWAY_ACCOUNT_ID), parseLong(chargeId));
        assertThat(events,  shouldIncludeStatus(statuses));
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
}
