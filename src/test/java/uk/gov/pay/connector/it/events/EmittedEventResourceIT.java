package uk.gov.pay.connector.it.events;

import io.dropwizard.core.setup.Environment;
import org.apache.commons.lang3.RandomUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.ConnectorModule;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.it.base.ITestBaseExtension;
import uk.gov.pay.connector.queue.statetransition.StateTransitionQueue;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.dropwizard.testing.ConfigOverride.config;
import static java.time.temporal.ChronoUnit.MICROS;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.util.AddChargeParams.AddChargeParamsBuilder.anAddChargeParams;

public class EmittedEventResourceIT {

    private static StateTransitionQueue stateTransitionQueue = new StateTransitionQueue();
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension(
            EmittedEventResourceIT.ConnectorAppWithCustomStateTransitionQueue.class,
            config("emittedEventSweepConfig.notEmittedEventMaxAgeInSeconds", "0")
    );
    
    @RegisterExtension
    public static ITestBaseExtension testBaseExtension = new ITestBaseExtension("sandbox", app.getLocalPort(), app.getDatabaseTestHelper());
    private String externalChargeId;
    
    @BeforeEach
    void setUp() {
        stateTransitionQueue.clear();
    }

    @Test
    void shouldSweepEmittedEventsIfDoNotRetryEmitUntilIsNull() {
        long chargeId = addCharge();
        app.getDatabaseTestHelper().addEvent(chargeId, CREATED.toString());

        app.getDatabaseTestHelper().addEmittedEvent("payment", externalChargeId, Instant.now(),
                "PAYMENT_CREATED", null, null);

        testBaseExtension.getConnectorRestApiClient()
                .postEmittedEventsSweepTask()
                .statusCode(OK.getStatusCode());

        assertThat(stateTransitionQueue.size(), is(1));

        List<Map<String, Object>> emittedEvents = app.getDatabaseTestHelper().readEmittedEvents();

        // emitted events sweeper adds duplicate event to table (and doesn't remove existing event)
        assertThat(emittedEvents.size(), is(2));
        assertEmittedEvent(emittedEvents.getFirst(), null);
        assertEmittedEvent(emittedEvents.get(1), null);
    }

    @Test
    void shouldSweepEmittedEventsIfDoNotRetryEmitUntilValueIsInThePast() {
        long chargeId = addCharge();
        app.getDatabaseTestHelper().addEvent(chargeId, CREATED.toString());

        Instant doNotRetryEmitUntil = Instant.now().minusSeconds(60).truncatedTo(MICROS);
        app.getDatabaseTestHelper().addEmittedEvent("payment", externalChargeId, Instant.now(),
                "PAYMENT_CREATED", null, doNotRetryEmitUntil);

        testBaseExtension.getConnectorRestApiClient()
                .postEmittedEventsSweepTask()
                .statusCode(OK.getStatusCode());

        assertThat(stateTransitionQueue.size(), is(1));

        List<Map<String, Object>> emittedEvents = app.getDatabaseTestHelper().readEmittedEvents();

        // emitted events sweeper adds duplicate event to table (and doesn't remove existing event)
        assertThat(emittedEvents.size(), is(2));

        assertEmittedEvent(emittedEvents.getFirst(), doNotRetryEmitUntil);
        assertEmittedEvent(emittedEvents.get(1), null);
    }

    @Test
    void shouldNotSweepEmittedEventsIfDoNotRetryEmitUntilValueIsInTheFuture() {
        long chargeId = addCharge();
        app.getDatabaseTestHelper().addEvent(chargeId, CREATED.toString());

        Instant doNotRetryEmitUntil = Instant.now().plusSeconds(60).truncatedTo(MICROS);
        app.getDatabaseTestHelper().addEmittedEvent("payment", externalChargeId, Instant.now(),
                "PAYMENT_CREATED", null, doNotRetryEmitUntil);

        testBaseExtension.getConnectorRestApiClient()
                .postEmittedEventsSweepTask()
                .statusCode(OK.getStatusCode());

        assertThat(stateTransitionQueue.size(), is(0));

        List<Map<String, Object>> emittedEvents = app.getDatabaseTestHelper().readEmittedEvents();

        assertThat(emittedEvents.size(), is(1));
        assertEmittedEvent(emittedEvents.getFirst(), doNotRetryEmitUntil);
    }

    private long addCharge() {
        long chargeId = RandomUtils.nextInt();
        externalChargeId = "charge" + chargeId;
        app.getDatabaseTestHelper().addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withGatewayAccountId(testBaseExtension.getAccountId())
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
