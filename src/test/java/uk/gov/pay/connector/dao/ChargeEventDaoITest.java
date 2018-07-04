package uk.gov.pay.connector.dao;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.events.EventCommandHandler;
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
import static org.mockito.Mockito.mock;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_READY;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_READY;
import static uk.gov.pay.connector.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;

public class ChargeEventDaoITest extends DaoITestBase {

    private ChargeDao chargeDao;
    private ChargeEventDao chargeEventDao;
    private DatabaseFixtures.TestCardDetails defaultTestCardDetails;
    private DatabaseFixtures.TestAccount defaultTestAccount;
    private EventCommandHandler eventCommandHandler = mock(EventCommandHandler.class);

    @Before
    public void setUp() throws Exception {
        chargeDao = env.getInstance(ChargeDao.class);
        chargeEventDao = env.getInstance(ChargeEventDao.class);
        defaultTestCardDetails = new DatabaseFixtures(databaseTestHelper).validTestCardDetails();
        this.defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .insert();
    }

    @Test
    public void persistChargeEventOfChargeEntity_succeeds() throws Exception {

        Long chargeId = 56735L;
        String externalChargeId = "charge456";

        String transactionId = "345654";
        String transactionId2 = "345655";
        String transactionId3 = "345656";

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
        entity.setStatus(ENTERING_CARD_DETAILS, eventCommandHandler);

        chargeEventDao.persistChargeEventOf(entity, Optional.empty());

        //move status to AUTHORISED
        entity.setStatus(AUTHORISATION_READY, eventCommandHandler);
        entity.setStatus(AUTHORISATION_SUCCESS, eventCommandHandler);
        entity.setGatewayTransactionId(transactionId2);

        chargeEventDao.persistChargeEventOf(entity, Optional.empty());

        entity.setStatus(CAPTURE_READY, eventCommandHandler);
        entity.setGatewayTransactionId(transactionId3);

        chargeEventDao.persistChargeEventOf(entity, Optional.empty());

        List<ChargeEventEntity> events = chargeDao.findById(chargeId).get().getEvents();

        assertThat(events, hasSize(3));
        assertThat(events, shouldIncludeStatus(ENTERING_CARD_DETAILS));
        assertThat(events, shouldIncludeStatus(AUTHORISATION_SUCCESS));
        assertThat(events, shouldIncludeStatus(CAPTURE_READY));
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
