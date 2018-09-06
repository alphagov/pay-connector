package uk.gov.pay.connector.dao;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.it.dao.DaoITestBase;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.exparity.hamcrest.date.ZonedDateTimeMatchers.within;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_READY;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AWAITING_CAPTURE_REQUEST;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_READY;
import static uk.gov.pay.connector.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;

public class ChargeEventDaoITest extends DaoITestBase {

    private static final long CHARGE_ID = 56735L;
    private static final String EXTERNAL_CHARGE_ID = "charge456";

    private static final String TRANSACTION_ID = "345654";
    private static final String TRANSACTION_ID_2 = "345655";
    private static final String TRANSACTION_ID_3 = "345656";

    private ChargeDao chargeDao;
    private ChargeEventDao chargeEventDao;
    private DatabaseFixtures.TestAccount defaultTestAccount;

    @Before
    public void setUp() {
        chargeDao = env.getInstance(ChargeDao.class);
        chargeEventDao = env.getInstance(ChargeEventDao.class);
        this.defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .insert();
    }

    @Test
    public void persistChargeEventOfChargeEntity_succeeds() {

        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(CHARGE_ID)
                .withExternalChargeId(EXTERNAL_CHARGE_ID)
                .withTransactionId(TRANSACTION_ID)
                .insert();

        Optional<ChargeEntity> charge = chargeDao.findById(CHARGE_ID);
        ChargeEntity entity = charge.get();
        entity.setStatus(ENTERING_CARD_DETAILS);

        chargeEventDao.persistChargeEventOf(entity, Optional.empty());

        //move status to AUTHORISED
        entity.setStatus(AUTHORISATION_READY);
        entity.setStatus(AUTHORISATION_SUCCESS);
        entity.setGatewayTransactionId(TRANSACTION_ID_2);

        chargeEventDao.persistChargeEventOf(entity, Optional.empty());

        entity.setStatus(CAPTURE_READY);
        entity.setGatewayTransactionId(TRANSACTION_ID_3);

        chargeEventDao.persistChargeEventOf(entity, Optional.empty());

        List<ChargeEventEntity> events = chargeDao.findById(CHARGE_ID).get().getEvents();

        assertThat(events, hasSize(3));
        assertThat(events, shouldIncludeStatus(ENTERING_CARD_DETAILS));
        assertThat(events, shouldIncludeStatus(AUTHORISATION_SUCCESS));
        assertThat(events, shouldIncludeStatus(CAPTURE_READY));
        assertDateMatch(events.get(0).getUpdated());
    }

    @Test
    public void shouldPersistEventForStatus_awaitingCaptureRequest() {

        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(CHARGE_ID)
                .withExternalChargeId(EXTERNAL_CHARGE_ID)
                .withTransactionId(TRANSACTION_ID)
                .withDelayedCapture(true)
                .insert();

        Optional<ChargeEntity> charge = chargeDao.findById(CHARGE_ID);
        assertThat(charge.isPresent(), is(true));
        ChargeEntity entity = charge.get();
        entity.setStatus(ENTERING_CARD_DETAILS);

        chargeEventDao.persistChargeEventOf(entity, Optional.empty());

        //move status to AUTHORISED
        entity.setStatus(AUTHORISATION_READY);
        entity.setStatus(AUTHORISATION_SUCCESS);
        entity.setGatewayTransactionId(TRANSACTION_ID_2);

        chargeEventDao.persistChargeEventOf(entity, Optional.empty());

        entity.setStatus(AWAITING_CAPTURE_REQUEST);
        entity.setGatewayTransactionId(TRANSACTION_ID_3);

        chargeEventDao.persistChargeEventOf(entity, Optional.empty());

        final Optional<ChargeEntity> maybeChargeEntity = chargeDao.findById(CHARGE_ID);
        assertThat(maybeChargeEntity.isPresent(), is(true));
        List<ChargeEventEntity> events = maybeChargeEntity.get().getEvents();

        assertThat(events, hasSize(3));
        assertThat(events, shouldIncludeStatus(ENTERING_CARD_DETAILS));
        assertThat(events, shouldIncludeStatus(AUTHORISATION_SUCCESS));
        assertThat(events, shouldIncludeStatus(AWAITING_CAPTURE_REQUEST));
        assertDateMatch(events.get(0).getUpdated());
    }

    private void assertDateMatch(ZonedDateTime createdDateTime) {
        assertThat(createdDateTime, within(1, ChronoUnit.MINUTES, ZonedDateTime.now()));
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
}
