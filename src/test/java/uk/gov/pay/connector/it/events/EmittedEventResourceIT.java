package uk.gov.pay.connector.it.events;

import io.dropwizard.setup.Environment;
import org.apache.commons.lang.math.RandomUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.ConnectorModule;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.junit.ConfigOverride;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.queue.StateTransitionQueue;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static javax.ws.rs.core.Response.Status.OK;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.util.AddChargeParams.AddChargeParamsBuilder.anAddChargeParams;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(
        app = EmittedEventResourceIT.ConnectorAppWithCustomStateTransitionQueue.class,
        config = "config/test-it-config.yaml",
        withDockerSQS = true,
        configOverrides = {
                @ConfigOverride(key = "emittedEventSweepConfig.notEmittedEventMaxAgeInSeconds", value = "0")
        }
)
public class EmittedEventResourceIT extends ChargingITestBase {

    private static StateTransitionQueue stateTransitionQueue = new StateTransitionQueue();
    private String externalChargeId;

    public EmittedEventResourceIT() {
        super("sandbox");
    }

    @Override
    @Before
    public void setUp() {
        super.setUp();
        databaseTestHelper.truncateEmittedEvents();
        stateTransitionQueue.clear();
    }

    @Test
    public void shouldSweepEmittedEventsIfDoNotRetryEmitUntilIsNull() {
        long chargeId = addCharge();
        databaseTestHelper.addEvent(chargeId, CREATED.toString());

        databaseTestHelper.addEmittedEvent("payment", externalChargeId, Instant.now(),
                "PAYMENT_CREATED", null, null);

        connectorRestApiClient
                .postEmittedEventsSweepTask()
                .statusCode(OK.getStatusCode());

        assertThat(stateTransitionQueue.size(), is(1));

        List<Map<String, Object>> emittedEvents = databaseTestHelper.readEmittedEvents();

        // emitted events sweeper adds duplicate event to table (and doesn't remove existing event)
        assertThat(emittedEvents.size(), is(2));
        assertEmittedEvent(emittedEvents.get(0), null);
        assertEmittedEvent(emittedEvents.get(1), null);
    }

    @Test
    public void shouldSweepEmittedEventsIfDoNotRetryEmitUntilValueIsInThePast() {
        long chargeId = addCharge();
        databaseTestHelper.addEvent(chargeId, CREATED.toString());

        Instant doNotRetryEmitUntil = Instant.now().minusSeconds(60);
        databaseTestHelper.addEmittedEvent("payment", externalChargeId, Instant.now(),
                "PAYMENT_CREATED", null, doNotRetryEmitUntil);

        connectorRestApiClient
                .postEmittedEventsSweepTask()
                .statusCode(OK.getStatusCode());

        assertThat(stateTransitionQueue.size(), is(1));

        List<Map<String, Object>> emittedEvents = databaseTestHelper.readEmittedEvents();

        // emitted events sweeper adds duplicate event to table (and doesn't remove existing event)
        assertThat(emittedEvents.size(), is(2));

        assertEmittedEvent(emittedEvents.get(0), doNotRetryEmitUntil);
        assertEmittedEvent(emittedEvents.get(1), null);
    }

    @Test
    public void shouldNotSweepEmittedEventsIfDoNotRetryEmitUntilValueIsInTheFuture() {
        long chargeId = addCharge();
        databaseTestHelper.addEvent(chargeId, CREATED.toString());

        Instant doNotRetryEmitUntil = Instant.now().plusSeconds(60);
        databaseTestHelper.addEmittedEvent("payment", externalChargeId, Instant.now(),
                "PAYMENT_CREATED", null, doNotRetryEmitUntil);

        connectorRestApiClient
                .postEmittedEventsSweepTask()
                .statusCode(OK.getStatusCode());

        assertThat(stateTransitionQueue.size(), is(0));

        List<Map<String, Object>> emittedEvents = databaseTestHelper.readEmittedEvents();

        assertThat(emittedEvents.size(), is(1));
        assertEmittedEvent(emittedEvents.get(0), doNotRetryEmitUntil);
    }

    private long addCharge() {
        long chargeId = RandomUtils.nextInt();
        externalChargeId = "charge" + chargeId;
        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withGatewayAccountId(accountId)
                .withAmount(100)
                .withStatus(CREATED)
                .build());
        return chargeId;
    }

    private void assertEmittedEvent(Map<String, Object> emittedEvent, Instant expectedDoNotRetryEmitUntil) {
        assertThat(emittedEvent.get("event_type"), is("PAYMENT_CREATED"));
        Optional.ofNullable(expectedDoNotRetryEmitUntil).ifPresentOrElse(
                value -> assertThat(emittedEvent.get("do_not_retry_emit_until"), is(Timestamp.from(expectedDoNotRetryEmitUntil))),
                () -> assertThat(emittedEvent.get("do_not_retry_emit_until"), is(nullValue()))
        );
    }

    public static class ConnectorAppWithCustomStateTransitionQueue extends ConnectorApp {

        @Override
        protected ConnectorModule getModule(ConnectorConfiguration configuration, Environment environment) {
            return new EmittedEventResourceIT.ConnectorModuleWithOverrides(configuration, environment);
        }
    }

    private static class ConnectorModuleWithOverrides extends ConnectorModule {

        public ConnectorModuleWithOverrides(ConnectorConfiguration configuration, Environment environment) {
            super(configuration, environment);
        }

        @Override
        protected StateTransitionQueue getStateTransitionQueue() {
            return stateTransitionQueue;
        }
    }
}
