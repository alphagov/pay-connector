package uk.gov.pay.connector.it.dao;

import org.apache.commons.collections4.CollectionUtils;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.dao.EventDao;
import uk.gov.pay.connector.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.time.LocalDateTime.now;
import static java.time.temporal.ChronoUnit.MINUTES;
import static java.util.Arrays.asList;
import static org.exparity.hamcrest.date.LocalDateTimeMatchers.within;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class EventDaoJpaTest {

    private static final Long GATEWAY_ACCOUNT_ID = 564532435L;
    private static final Long CHARGE_ID = 123456L;
    private static final long AMOUNT = 10300L;
    private static final String TRANSACTION_ID = UUID.randomUUID().toString();
    private static final String RETURN_URL = "http://some.valid.url/";

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();
    public GuicedTestEnvironment env;
    private EventDao eventDao;

    @Before
    public void setUp() throws Exception {
        env = GuicedTestEnvironment.from(app.getPersistModule()).start();

        eventDao = env.getInstance(EventDao.class);

        app.getDatabaseTestHelper().addGatewayAccount(GATEWAY_ACCOUNT_ID.toString(), "test_account");
        app.getDatabaseTestHelper().addCharge(CHARGE_ID.toString(), GATEWAY_ACCOUNT_ID.toString(), AMOUNT, CAPTURED, RETURN_URL, TRANSACTION_ID);
    }

    @After
    public void tearDown() {
        env.stop();
    }

    @Test
    public void shouldRetrieveAllEventsForAGivenCharge() throws Exception {
        List<ChargeStatus> statuses = asList(CREATED, ENTERING_CARD_DETAILS, AUTHORISATION_READY, AUTHORISATION_SUCCESS, CAPTURE_SUBMITTED, CAPTURED);
        setupLifeCycleEventsFor(app, CHARGE_ID, statuses);
        List<ChargeEventEntity> events = eventDao.findEvents(GATEWAY_ACCOUNT_ID, CHARGE_ID);

        assertThat(events.size(), is(statuses.size()));
        assertThat(events, containsStatuses(statuses));

        events.stream().forEach(event -> assertThat(event.getUpdated(), is(within(1, MINUTES, now()))));
    }

    @Test
    public void shouldNotReturnEventsIfChargeDoesNotBelongToAccount() throws Exception {

        List<ChargeStatus> statuses = asList(CREATED, ENTERING_CARD_DETAILS);
        setupLifeCycleEventsFor(app, CHARGE_ID, statuses);
        Long nonExistentAccountId = 1111L;
        List<ChargeEventEntity> events = eventDao.findEvents(nonExistentAccountId, CHARGE_ID);

        assertThat(events.size(), is(0));
    }

    private Matcher<? super List<ChargeEventEntity>> containsStatuses(final List<ChargeStatus> expected) {
        return new TypeSafeMatcher<List<ChargeEventEntity>>() {
            private List<ChargeStatus> chargeStatuses;

            @Override
            protected boolean matchesSafely(List<ChargeEventEntity> chargeEvents) {
                this.chargeStatuses = chargeEvents.stream()
                        .map(ChargeEventEntity::getStatus)
                        .collect(Collectors.toList());
                return CollectionUtils.containsAll(chargeStatuses, expected);
            }

            @Override
            public void describeTo(Description description) {
                description.appendText(String.format("expected [%s] but was [%s]", expected, chargeStatuses));
            }
        };
    }

    public static void setupLifeCycleEventsFor(DropwizardAppWithPostgresRule app, Long chargeId, List<ChargeStatus> statuses) {
        statuses.stream().forEach(
                st -> app.getDatabaseTestHelper().addEvent(chargeId, st.getValue())
        );
    }
}
