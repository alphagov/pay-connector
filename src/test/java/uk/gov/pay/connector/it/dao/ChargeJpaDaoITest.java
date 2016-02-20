package uk.gov.pay.connector.it.dao;

import com.google.common.collect.ImmutableMap;
import org.exparity.hamcrest.date.ZonedDateTimeMatchers;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.MatcherAssert;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import uk.gov.pay.connector.dao.ChargeJpaDao;
import uk.gov.pay.connector.dao.EventJpaDao;
import uk.gov.pay.connector.dao.GatewayAccountJpaDao;
import uk.gov.pay.connector.dao.PayDBIException;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.util.ChargeEventListener;
import uk.gov.pay.connector.util.DateTimeUtils;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static uk.gov.pay.connector.model.api.ExternalChargeStatus.EXT_CREATED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

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
    private GatewayAccountJpaDao gatewayAccountJpaDao;
    private Long chargeId;
    private EventJpaDao eventDao;
    private ChargeEventListener eventListener;
    private DateTimeFormatter formatter;
    private GatewayAccountEntity gatewayAccountEntity;
    public GuicedTestEnvironment env;


    @Before
    public void setUp() throws Exception {
        env = GuicedTestEnvironment.from(app.getPersistModule())
                .start();

        chargeDao = env.getInstance(ChargeJpaDao.class);
        eventDao = env.getInstance(EventJpaDao.class);
        gatewayAccountJpaDao = env.getInstance(GatewayAccountJpaDao.class);

        app.getDatabaseTestHelper().addGatewayAccount(GATEWAY_ACCOUNT_ID.toString(), PAYMENT_PROVIDER);
        formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        gatewayAccountEntity = gatewayAccountJpaDao.findById(GatewayAccountEntity.class, GATEWAY_ACCOUNT_ID).get();
        ChargeEntity charge = new ChargeEntity(AMOUNT, CREATED.getValue(), "", "", DESCRIPTION, REFERENCE, gatewayAccountEntity);
        chargeDao.persist(charge);
        chargeId = charge.getId();
    }


    @Test
    public void insertANewChargeAndReturnTheId() throws Exception {
        ChargeEntity chargeEntity = newChargeEntity(AMOUNT, REFERENCE);
        chargeDao.persist(chargeEntity);
        assertThat(chargeEntity.getId(), is(notNullValue()));

        assertThat(app.getDatabaseTestHelper().getChargeStatus(chargeEntity.getId().toString()), is("CREATED"));
    }

    @Test
    public void searchChargesByFullReferenceOnly() throws Exception {
        List<ChargeEntity> charges = chargeDao.findAllBy(GATEWAY_ACCOUNT_ID, REFERENCE, null, null, null);
        assertThat(charges.size(), is(1));
        ChargeEntity charge = charges.get(0);

        assertThat(charge.getId(), is(chargeId));
        assertThat(charge.getAmount(), is(AMOUNT));
        assertThat(charge.getReference(), is(REFERENCE));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getStatus(), is(CREATED.getValue()));

        assertDateMatch(charge.getCreatedDate().toString());
    }

    @Test
    public void searchChargesByPartialReferenceOnly() throws Exception {
        ChargeEntity newCharge = new ChargeEntity(AMOUNT, CREATED.getValue(), UUID.randomUUID().toString(),
                RETURN_URL, DESCRIPTION, COUNCIL_TAX_PAYMENT_REFERENCE, gatewayAccountEntity);
        chargeDao.persist(newCharge);
        List<ChargeEntity> charges = chargeDao.findAllBy(GATEWAY_ACCOUNT_ID, "reference", null, null, null);
        assertThat(charges.size(), is(2));
        assertThat(charges.get(1).getId(), is(chargeId));
        assertThat(charges.get(1).getReference(), is(REFERENCE));
        assertThat(charges.get(0).getReference(), is(COUNCIL_TAX_PAYMENT_REFERENCE));

        for(ChargeEntity charge : charges) {
            assertThat(charge.getAmount(), is(AMOUNT));
            assertThat(charge.getDescription(), is(DESCRIPTION));
            assertThat(charge.getStatus(), is(CREATED.getValue()));
            assertDateMatch(charge.getCreatedDate().toString());
        }
    }

    @Test
    public void searchChargeByReferenceAndStatusOnly() throws Exception {
        List<ChargeEntity> charges = chargeDao.findAllBy(GATEWAY_ACCOUNT_ID, REFERENCE, EXT_CREATED, null, null);
        assertThat(charges.size(), is(1));
        ChargeEntity charge = charges.get(0);

        assertThat(charge.getId(), is(chargeId));
        assertThat(charge.getAmount(), is(AMOUNT));
        assertThat(charge.getReference(), is(REFERENCE));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getStatus(), is(CREATED.getValue()));
        assertDateMatch(charge.getCreatedDate().toString());
    }

    @Test
    public void searchChargeByReferenceAndStatusAndFromDateAndToDate() throws Exception {
        List<ChargeEntity> charges = chargeDao.findAllBy(GATEWAY_ACCOUNT_ID, REFERENCE, EXT_CREATED, FROM_DATE, TO_DATE);
        assertThat(charges.size(), is(1));
        ChargeEntity charge = charges.get(0);

        assertThat(charge.getId(), is(chargeId));
        assertThat(charge.getAmount(), is(AMOUNT));
        assertThat(charge.getReference(), is(REFERENCE));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getStatus(), is(CREATED.getValue()));
        assertDateMatch(charge.getCreatedDate().toString());
    }

    @Test
    public void searchChargeByReferenceAndStatusAndFromDate() throws Exception {
        List<ChargeEntity> charges = chargeDao.findAllBy(GATEWAY_ACCOUNT_ID, REFERENCE, EXT_CREATED, FROM_DATE, null);
        assertThat(charges.size(), is(1));
        ChargeEntity charge = charges.get(0);

        assertThat(charge.getId(), is(chargeId));
        assertThat(charge.getAmount(), is(AMOUNT));
        assertThat(charge.getReference(), is(REFERENCE));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getStatus(), is(CREATED.getValue()));
        assertDateMatch(charge.getCreatedDate().toString());
    }

    @Test
    public void searchChargeByReferenceAndStatusAndToDate() throws Exception {
        List<ChargeEntity> charges = chargeDao.findAllBy(GATEWAY_ACCOUNT_ID, REFERENCE, EXT_CREATED, null, TO_DATE);
        assertThat(charges.size(), is(1));
        ChargeEntity charge = charges.get(0);

        assertThat(charge.getId(), is(chargeId));
        assertThat(charge.getAmount(), is(AMOUNT));
        assertThat(charge.getReference(), is(REFERENCE));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getStatus(), is(CREATED.getValue()));
        assertDateMatch(charge.getCreatedDate().toString());
    }

    @Test
    public void searchChargeByReferenceAndStatusAndFromDate_ShouldReturnZero() throws Exception {
        List<ChargeEntity> charges = chargeDao.findAllBy(GATEWAY_ACCOUNT_ID, REFERENCE, EXT_CREATED, TO_DATE, null);
        assertThat(charges.size(), is(0));
    }

    @Test
    public void searchChargeByReferenceAndStatusAndToDate_ShouldReturnZero() throws Exception {
        List<ChargeEntity> charges = chargeDao.findAllBy(GATEWAY_ACCOUNT_ID, REFERENCE, EXT_CREATED, null, FROM_DATE);
        assertThat(charges.size(), is(0));
    }

    @Test
    public void shouldUpdateCharge() throws Exception {
        ChargeEntity charge = new ChargeEntity(AMOUNT, CREATED.getValue(), UUID.randomUUID().toString(), RETURN_URL, "", REFERENCE, gatewayAccountEntity);
        chargeDao.persist(charge);
        charge = chargeDao.findById(charge.getId()).get();


        charge.setStatus(AUTHORISATION_SUBMITTED);
        charge.setGatewayTransactionId("new-gateway-transaction-id");
        chargeDao.merge(charge);

        ChargeEntity chargeFromDB = chargeDao.findById(ChargeEntity.class, charge.getId()).get();
        assertThat(chargeFromDB.getStatus(), is(AUTHORISATION_SUBMITTED.getValue()));
        assertThat(chargeFromDB.getGatewayTransactionId(), is("new-gateway-transaction-id"));

        assertLoggedEvents(charge.getId(), AUTHORISATION_SUBMITTED);
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

    @Test
    public void shouldFindChargeEntityByGatewayTransactionIdAndProvider() throws Exception {

        Long id = System.currentTimeMillis();
        String transactionId = UUID.randomUUID().toString();
        app.getDatabaseTestHelper().addCharge(id.toString(),
                GATEWAY_ACCOUNT_ID.toString(), AMOUNT,CREATED, RETURN_URL, transactionId, REFERENCE, ZonedDateTime.now());

        Optional<ChargeEntity> charge = chargeDao.findByGatewayTransactionIdAndProvider(transactionId, PAYMENT_PROVIDER);

        assertTrue(charge.isPresent());
    }

//    @Test
//    public void updateStatusToEnteringCardDetailsFromCreated_shouldReturnOne() throws Exception {
//        List<ChargeStatus> oldStatuses = newArrayList(CREATED, ENTERING_CARD_DETAILS);
//        int rowsUpdated = chargeDao.updateNewStatusWhereOldStatusIn(chargeId, ENTERING_CARD_DETAILS, oldStatuses);
//        chargeDao.f
//        assertEquals(1, rowsUpdated);
//        ChargeEntity charge = chargeDao.findById(chargeId).get();
//        assertThat(charge.getId(), is(chargeId));
//        assertThat(charge.getStatus(), is(ENTERING_CARD_DETAILS.getValue()));
//
//        assertLoggedEvents(chargeId, CREATED, ENTERING_CARD_DETAILS);
//    }

//    @Test
//    public void updateStatusToEnteringCardDetailsFromCaptured_shouldReturnZero() throws Exception {
//        ChargeEntity charge = chargeDao.findById(chargeId).get();
//        charge.setStatus(CAPTURE_SUBMITTED);
//        chargeDao.merge(charge);
//        List<ChargeStatus> oldStatuses = newArrayList(CREATED, ENTERING_CARD_DETAILS);
//        int rowsUpdated = chargeDao.updateNewStatusWhereOldStatusIn(chargeId, ENTERING_CARD_DETAILS, oldStatuses);
//
//        assertEquals(0, rowsUpdated);
//        charge = chargeDao.findById(chargeId).get();
//        assertThat(charge.getId(), is(chargeId));
//        assertThat(charge.getStatus(), is(CAPTURE_SUBMITTED.getValue()));
//
//        List<ChargeEventEntity> events = eventDao.findEvents(GATEWAY_ACCOUNT_ID, chargeId);
//        assertThat(events, not(shouldIncludeStatus(ENTERING_CARD_DETAILS)));
//    }

    @Test
    public void throwDBIExceptionIfStatusNotUpdateForMissingCharge() throws Exception {
        String unknownId = "128457938450746";
        ChargeStatus status = AUTHORISATION_SUCCESS;

        expectedEx.expect(PayDBIException.class);
        expectedEx.expectMessage("Could not update charge '" + unknownId + "' with status " + status.toString());

        chargeDao.updateStatus(Long.valueOf(unknownId), status);
    }

    @Test
    public void invalidSizeOfFields() throws Exception {
        expectedEx.expect(RuntimeException.class);
        ChargeEntity charge = new ChargeEntity(AMOUNT, CREATED.getValue(), "", "", "", randomAlphanumeric(512), gatewayAccountEntity);
        chargeDao.persist(charge);
    }

    private void assertLoggedEvents(Long chargeId, ChargeStatus... statuses) {
        List<ChargeEventEntity> events = eventDao.findEvents(GATEWAY_ACCOUNT_ID,  chargeId);
        assertThat(events,  shouldIncludeStatus(statuses));
    }

    private Matcher<? super List<ChargeEventEntity>> shouldIncludeStatus(ChargeStatus... expectedStatuses) {
        return new TypeSafeMatcher<List<ChargeEventEntity>>() {
            @Override
            protected boolean matchesSafely(List<ChargeEventEntity> chargeEvents) {
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

    private ChargeEntity newChargeEntity(Long amount, String reference) {
        return new ChargeEntity(amount,
                CREATED.toString(),
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
