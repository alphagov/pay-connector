package uk.gov.pay.connector.it.dao;

import com.google.common.collect.ImmutableMap;
import org.exparity.hamcrest.date.ZonedDateTimeMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import uk.gov.pay.connector.dao.ChargeJpaDao;
import uk.gov.pay.connector.dao.EventDao;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.util.ChargeEventListener;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertTrue;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUBMITTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CREATED;

public class ChargeJpaDaoITest {
    private static final Long GATEWAY_ACCOUNT_ID = 564532435L;
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

    private ChargeJpaDao chargeDao;
    private Long chargeId;
    private EventDao eventDao;
    private ChargeEventListener eventListener;
    private DateTimeFormatter formatter;
    public GuicedTestEnvironment env;


    @Before
    public void setUp() throws Exception {
        env = GuicedTestEnvironment.from(app.getPersistModule())
                .start();

        chargeDao = env.getInstance(ChargeJpaDao.class);

        app.getDatabaseTestHelper().addGatewayAccount(GATEWAY_ACCOUNT_ID.toString(), PAYMENT_PROVIDER);
        formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    }


    @Test
    public void insertANewChargeAndReturnTheId() throws Exception {
        ChargeEntity chargeEntity = newChargeEntity(AMOUNT, REFERENCE);
        chargeDao.persist(chargeEntity);
        assertThat(chargeEntity.getId(), is(notNullValue()));

        assertThat(app.getDatabaseTestHelper().getChargeStatus(chargeEntity.getId().toString()), is("CREATED"));
    }

    //    @Test
//    public void searchChargesByFullReferenceOnly() throws Exception {
//        List<Map<String, Object>> charges = chargeDao.findAllBy(GATEWAY_ACCOUNT_ID, REFERENCE, null, null, null);
//        assertThat(charges.size(), is(1));
//        Map<String, Object> charge = charges.get(0);
//
//        assertThat(charge.get("charge_id"), is(chargeId));
//        assertThat(charge.get("amount"), is(AMOUNT));
//        assertThat(charge.get("reference"), is(REFERENCE));
//        assertThat(charge.get("description"), is(DESCRIPTION));
//        assertThat(charge.get("status"), is(CREATED.getValue()));
//
//        assertDateMatch(charge.get("created_date").toString());
//    }
//
//    @Test
//    public void searchChargesByPartialReferenceOnly() throws Exception {
//        List<Map<String, Object>> charges = chargeDao.findAllBy(GATEWAY_ACCOUNT_ID, "reference", null, null, null);
//        assertThat(charges.size(), is(2));
//        assertThat(charges.get(1).get("charge_id"), is(chargeId));
//        assertThat(charges.get(1).get("reference"), is(REFERENCE));
//        assertThat(charges.get(0).get("reference"), is(COUNCIL_TAX_PAYMENT_REFERENCE));
//
//        for(Map<String, Object> charge : charges) {
//            assertThat(charge.get("amount"), is(AMOUNT));
//            assertThat(charge.get("description"), is(DESCRIPTION));
//            assertThat(charge.get("status"), is(CREATED.getValue()));
//            assertDateMatch(charge.get("created_date").toString());
//        }
//    }
//
//    @Test
//    public void searchChargeByReferenceAndStatusOnly() throws Exception {
//        List<Map<String, Object>> charges = chargeDao.findAllBy(GATEWAY_ACCOUNT_ID, REFERENCE, ExternalChargeStatus.EXT_CREATED, null, null);
//        assertThat(charges.size(), is(1));
//        Map<String, Object> charge = charges.get(0);
//
//        assertThat(charge.get("charge_id"), is(chargeId));
//        assertThat(charge.get("amount"), is(AMOUNT));
//        assertThat(charge.get("reference"), is(REFERENCE));
//        assertThat(charge.get("description"), is(DESCRIPTION));
//        assertThat(charge.get("status"), is(CREATED.getValue()));
//        assertDateMatch(charge.get("created_date").toString());
//    }
//
//    @Test
//    public void searchChargeByReferenceAndStatusAndFromDateAndToDate() throws Exception {
//        List<Map<String, Object>> charges = chargeDao.findAllBy(GATEWAY_ACCOUNT_ID, REFERENCE, ExternalChargeStatus.EXT_CREATED, FROM_DATE, TO_DATE);
//        assertThat(charges.size(), is(1));
//        Map<String, Object> charge = charges.get(0);
//
//        assertThat(charge.get("charge_id"), is(chargeId));
//        assertThat(charge.get("amount"), is(AMOUNT));
//        assertThat(charge.get("reference"), is(REFERENCE));
//        assertThat(charge.get("description"), is(DESCRIPTION));
//        assertThat(charge.get("status"), is(CREATED.getValue()));
//        assertDateMatch(charge.get("created_date").toString());
//    }
//
//    @Test
//    public void searchChargeByReferenceAndStatusAndFromDate() throws Exception {
//        List<Map<String, Object>> charges = chargeDao.findAllBy(GATEWAY_ACCOUNT_ID, REFERENCE, ExternalChargeStatus.EXT_CREATED, FROM_DATE, null);
//        assertThat(charges.size(), is(1));
//        Map<String, Object> charge = charges.get(0);
//
//        assertThat(charge.get("charge_id"), is(chargeId));
//        assertThat(charge.get("amount"), is(AMOUNT));
//        assertThat(charge.get("reference"), is(REFERENCE));
//        assertThat(charge.get("description"), is(DESCRIPTION));
//        assertThat(charge.get("status"), is(CREATED.getValue()));
//        assertDateMatch(charge.get("created_date").toString());
//    }
//
//    @Test
//    public void searchChargeByReferenceAndStatusAndToDate() throws Exception {
//        List<Map<String, Object>> charges = chargeDao.findAllBy(GATEWAY_ACCOUNT_ID, REFERENCE, ExternalChargeStatus.EXT_CREATED, null, TO_DATE);
//        assertThat(charges.size(), is(1));
//        Map<String, Object> charge = charges.get(0);
//
//        assertThat(charge.get("charge_id"), is(chargeId));
//        assertThat(charge.get("amount"), is(AMOUNT));
//        assertThat(charge.get("reference"), is(REFERENCE));
//        assertThat(charge.get("description"), is(DESCRIPTION));
//        assertThat(charge.get("status"), is(CREATED.getValue()));
//        assertDateMatch(charge.get("created_date").toString());
//    }
//
//    @Test
//    public void searchChargeByReferenceAndStatusAndFromDate_ShouldReturnZero() throws Exception {
//        List<Map<String, Object>> charges = chargeDao.findAllBy(GATEWAY_ACCOUNT_ID, REFERENCE, ExternalChargeStatus.EXT_CREATED, TO_DATE, null);
//        assertThat(charges.size(), is(0));
//    }
//
//    @Test
//    public void searchChargeByReferenceAndStatusAndToDate_ShouldReturnZero() throws Exception {
//        List<Map<String, Object>> charges = chargeDao.findAllBy(GATEWAY_ACCOUNT_ID, REFERENCE, ExternalChargeStatus.EXT_CREATED, null, FROM_DATE);
//        assertThat(charges.size(), is(0));
//    }
//
    @Test
    public void shouldUpdateCharge() throws Exception {
        Long id = System.currentTimeMillis();
        app.getDatabaseTestHelper().addCharge(id.toString(),
                GATEWAY_ACCOUNT_ID.toString(), AMOUNT,CREATED, RETURN_URL, UUID.randomUUID().toString(), REFERENCE, ZonedDateTime.now());
        ChargeEntity charge = chargeDao.findById(id).get();


        charge.setStatus(AUTHORISATION_SUBMITTED);
        String newGatewayTransactionId = "new-gateway-transaction-id";
        charge.setGatewayTransactionId(newGatewayTransactionId);
        chargeDao.persist(charge);

        Map<String, Object> chargeFromDB = app.getDatabaseTestHelper().getCharge(id);
        assertThat(chargeFromDB.get("status").toString(), is(AUTHORISATION_SUBMITTED.getValue()));
        assertThat(chargeFromDB.get("gateway_transaction_id"), is(newGatewayTransactionId));

        // TODO: Enable this when the eventDao is completed.
//        assertLoggedEvents(AUTHORISATION_SUBMITTED);
    }

    @Test
    public void insertAmountAndThenGetAmountById() throws Exception {

        Long id = System.currentTimeMillis();
        app.getDatabaseTestHelper().addCharge(id.toString(),
                GATEWAY_ACCOUNT_ID.toString(), AMOUNT,CREATED, RETURN_URL, UUID.randomUUID().toString(), REFERENCE, ZonedDateTime.now());
        ChargeEntity charge = chargeDao.findById(id).get();

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
//

//
    @Test
    public void shouldFindChargeEntityByGatewayTransactionIdAndProvider() throws Exception {

        Long id = System.currentTimeMillis();
        String transactionId = UUID.randomUUID().toString();
        app.getDatabaseTestHelper().addCharge(id.toString(),
                GATEWAY_ACCOUNT_ID.toString(), AMOUNT,CREATED, RETURN_URL, transactionId, REFERENCE, ZonedDateTime.now());

        Optional<ChargeEntity> charge = chargeDao.findByGatewayTransactionIdAndProvider(transactionId, PAYMENT_PROVIDER);

        assertTrue(charge.isPresent());
    }
//
//    @Test
//    public void updateStatusToEnteringCardDetailsFromCreated_shouldReturnOne() throws Exception {
//        List<ChargeStatus> oldStatuses = newArrayList(CREATED, ENTERING_CARD_DETAILS);
//        int rowsUpdated = chargeDao.updateNewStatusWhereOldStatusIn(chargeId, ENTERING_CARD_DETAILS, oldStatuses);
//
//        assertEquals(1, rowsUpdated);
//        Map<String, Object> charge = chargeDao.findById(chargeId).get();
//        assertThat(charge.get("charge_id"), is(chargeId));
//        assertThat(charge.get("status"), is(ENTERING_CARD_DETAILS.getValue()));
//
//        assertLoggedEvents(CREATED, ENTERING_CARD_DETAILS);
//    }
//
//    @Test
//    public void updateStatusToEnteringCardDetailsFromCaptured_shouldReturnZero() throws Exception {
//        chargeDao.updateStatus(chargeId, CAPTURE_SUBMITTED);
//        List<ChargeStatus> oldStatuses = newArrayList(CREATED, ENTERING_CARD_DETAILS);
//        int rowsUpdated = chargeDao.updateNewStatusWhereOldStatusIn(chargeId, ENTERING_CARD_DETAILS, oldStatuses);
//
//        assertEquals(0, rowsUpdated);
//        Map<String, Object> charge = chargeDao.findById(chargeId).get();
//        assertThat(charge.get("charge_id"), is(chargeId));
//        assertThat(charge.get("status"), is(CAPTURE_SUBMITTED.getValue()));
//
//        List<ChargeEvent> events = eventDao.findEvents(parseLong(GATEWAY_ACCOUNT_ID), parseLong(chargeId));
//        assertThat(events, not(shouldIncludeStatus(ENTERING_CARD_DETAILS)));
//    }
//
//    @Test
//    public void throwDBIExceptionIfStatusNotUpdateForMissingCharge() throws Exception {
//        String unknownId = "128457938450746";
//        ChargeStatus status = AUTHORISATION_SUCCESS;
//
//        expectedEx.expect(PayDBIException.class);
//        expectedEx.expectMessage("Could not update charge '" + unknownId + "' with status " + status.toString());
//
//        chargeDao.updateStatus(unknownId, status);
//    }
//
//    @Test
//    public void invalidSizeOfFields() throws Exception {
//        expectedEx.expect(RuntimeException.class);
//        Map<String, Object> chargeData = new HashMap<>(newCharge(AMOUNT, REFERENCE));
//        chargeData.put("reference", randomAlphanumeric(512));
//        chargeId = chargeDao.saveNewCharge(GATEWAY_ACCOUNT_ID, chargeData);
//    }
//
//    private void assertLoggedEvents(ChargeStatus... statuses) {
//        List<ChargeEvent> events = eventDao.findEvents(parseLong(GATEWAY_ACCOUNT_ID), parseLong(chargeId));
//        assertThat(events,  shouldIncludeStatus(statuses));
//    }
//
//    private Matcher<? super List<ChargeEvent>> shouldIncludeStatus(ChargeStatus... expectedStatuses) {
//        return new TypeSafeMatcher<List<ChargeEvent>>() {
//            @Override
//            protected boolean matchesSafely(List<ChargeEvent> chargeEvents) {
//                List<ChargeStatus> actualStatuses = chargeEvents.stream().map(ce -> ce.getStatus()).collect(toList());
//                return actualStatuses.containsAll(asList(expectedStatuses));
//            }
//
//            @Override
//            public void describeTo(Description description) {
//                description.appendText(String.format("does not contain [%s]", expectedStatuses));
//            }
//        };
//    }
//
//    private ImmutableMap<String, Object> newCharge(long amount, String reference) {
//        return ImmutableMap.of(
//                "amount", amount,
//                "reference", reference,
//                "description", DESCRIPTION,
//                "return_url", RETURN_URL);
//    }
//
//    private void assertDateMatch(String createdDateString) {
//        ZonedDateTime createdDateTime = DateTimeUtils.toUTCZonedDateTime(createdDateString).get();
//        assertThat(createdDateTime, ZonedDateTimeMatchers.within(1, ChronoUnit.MINUTES, ZonedDateTime.now()));
//    }

    private ChargeEntity newChargeEntity(Long amount, String reference) {
        return new ChargeEntity(amount,
                ChargeStatus.CREATED.toString(),
                UUID.randomUUID().toString(),
                RETURN_URL,
                DESCRIPTION,
                REFERENCE,
                newGatewayAccountEntity(GATEWAY_ACCOUNT_ID)
        );
    }

    private GatewayAccountEntity newGatewayAccountEntity(Long id) {
        GatewayAccountEntity gatewayAccountEntity = new GatewayAccountEntity("sanbox", new HashMap<>());
        gatewayAccountEntity.setId(id);

        return gatewayAccountEntity;
    }
}
