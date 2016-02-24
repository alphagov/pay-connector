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
import uk.gov.pay.connector.dao.*;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeEvent;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.util.DateTimeUtils;

import java.time.ZonedDateTime;
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
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertTrue;
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


    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    private ChargeJpaDao chargeDao;
    private GatewayAccountJpaDao gatewayAccountJpaDao;
    private Long chargeId;
    private EventJpaDao eventDao;
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

        gatewayAccountEntity = gatewayAccountJpaDao.findById(GatewayAccountEntity.class, GATEWAY_ACCOUNT_ID).get();
        ChargeEntity charge = new ChargeEntity(AMOUNT, CREATED.getValue(), "", "", DESCRIPTION,
                REFERENCE, gatewayAccountEntity);
        chargeDao.persist(charge);
        chargeId = charge.getId();
    }

    @Test
    public void insertANewChargeAndReturnTheId() throws Exception {

        // given
        GatewayAccountEntity gatewayAccountEntity = new GatewayAccountEntity("sanbox", new HashMap<>());
        gatewayAccountEntity.setId(GATEWAY_ACCOUNT_ID);

        ChargeEntity chargeEntity = new ChargeEntity(AMOUNT,
                CREATED.toString(),
                UUID.randomUUID().toString(),
                RETURN_URL,
                DESCRIPTION,
                REFERENCE,
                gatewayAccountEntity
        );

        // when
        chargeDao.persist(chargeEntity);

        // then
        assertThat(chargeEntity.getId(), is(notNullValue()));
        assertThat(app.getDatabaseTestHelper().getChargeStatus(chargeEntity.getId().toString()), is("CREATED"));
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
        assertThat(charge.getId(), is(chargeId));
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
        assertThat(charge.getId(), is(chargeId));
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
        app.getDatabaseTestHelper().addCharge(chargeId.toString(), gatewayAccountEntity.getId().toString(), AMOUNT, CREATED, RETURN_URL, UUID.randomUUID().toString(), paymentReference, ZonedDateTime.now());

        ChargeSearchQuery queryBuilder = new ChargeSearchQuery(GATEWAY_ACCOUNT_ID)
                .withReferenceLike("reference");

        // when
        List<ChargeEntity> charges = chargeDao.findAllBy(queryBuilder);

        // then
        assertThat(charges.size(), is(2));
        assertThat(charges.get(1).getId(), is(this.chargeId));
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

        assertThat(charge.getId(), is(chargeId));
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

        assertThat(charge.getId(), is(chargeId));
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

        assertThat(charge.getId(), is(chargeId));
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
        app.getDatabaseTestHelper().addCharge(chargeId.toString(), gatewayAccountEntity.getId().toString(), AMOUNT, ENTERING_CARD_DETAILS, RETURN_URL, UUID.randomUUID().toString(), REFERENCE, ZonedDateTime.now());

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

        assertThat(charge.getId(), is(chargeId));
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
    public void shouldUpdateCharge() throws Exception {

        // given
        Long chargeId = System.currentTimeMillis();
        app.getDatabaseTestHelper().addCharge(chargeId.toString(), gatewayAccountEntity.getId().toString(), AMOUNT, CREATED, RETURN_URL, UUID.randomUUID().toString(), REFERENCE, ZonedDateTime.now());

        // when
        ChargeEntity charge = chargeDao.findById(chargeId).get();

        // then
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

        // given
        Long id = System.currentTimeMillis();
        app.getDatabaseTestHelper().addCharge(id.toString(),
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
    public void shouldFindChargeEntityByGatewayTransactionIdAndProvider() throws Exception {

        Long id = System.currentTimeMillis();
        String transactionId = UUID.randomUUID().toString();
        app.getDatabaseTestHelper().addCharge(id.toString(),
                GATEWAY_ACCOUNT_ID.toString(), AMOUNT, CREATED, RETURN_URL, transactionId, REFERENCE, ZonedDateTime.now());

        Optional<ChargeEntity> charge = chargeDao.findByGatewayTransactionIdAndProvider(transactionId, PAYMENT_PROVIDER);

        assertTrue(charge.isPresent());
    }

    @Test
    public void updateStatusToEnteringCardDetailsFromCreated_shouldReturnOne() throws Exception {

        List<ChargeStatus> oldStatuses = newArrayList(CREATED, ENTERING_CARD_DETAILS);

        chargeDao.updateNewStatusWhereOldStatusIn(chargeId, ENTERING_CARD_DETAILS, oldStatuses);

        ChargeEntity charge = chargeDao.findById(chargeId).get();

        assertThat(charge.getId(), is(chargeId));
        assertThat(charge.getStatus(), is(ENTERING_CARD_DETAILS.getValue()));
        assertLoggedEvents(chargeId, CREATED, ENTERING_CARD_DETAILS);
    }

    @Test
    public void updateStatusToEnteringCardDetailsFromCaptured_shouldReturnZero() throws Exception {

        ChargeEntity charge = chargeDao.findById(chargeId).get();
        charge.setStatus(CAPTURE_SUBMITTED);

        chargeDao.merge(charge);
        List<ChargeStatus> oldStatuses = newArrayList(CREATED, ENTERING_CARD_DETAILS);

        chargeDao.updateNewStatusWhereOldStatusIn(chargeId, ENTERING_CARD_DETAILS, oldStatuses);

        charge = chargeDao.findById(chargeId).get();
        assertThat(charge.getId(), is(chargeId));
        assertThat(charge.getStatus(), is(CAPTURE_SUBMITTED.getValue()));

        List<ChargeEvent> events = eventDao.findEvents(GATEWAY_ACCOUNT_ID, chargeId);
        assertThat(events, not(shouldIncludeStatus(ENTERING_CARD_DETAILS)));
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
    public void invalidSizeOfFields() throws Exception {
        expectedEx.expect(RuntimeException.class);
        ChargeEntity charge = new ChargeEntity(AMOUNT, CREATED.getValue(), "", "", "", randomAlphanumeric(512), gatewayAccountEntity);
        chargeDao.persist(charge);
    }

    private void assertLoggedEvents(Long chargeId, ChargeStatus... statuses) {
        List<ChargeEvent> events = eventDao.findEvents(GATEWAY_ACCOUNT_ID, chargeId);
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
}
