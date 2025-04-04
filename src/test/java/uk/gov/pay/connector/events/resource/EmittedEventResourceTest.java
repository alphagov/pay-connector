package uk.gov.pay.connector.events.resource;

import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.gov.pay.connector.events.EmittedEventsBackfillService;
import uk.gov.pay.connector.events.HistoricalEventEmitterService;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.time.ZonedDateTime;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;

@ExtendWith(DropwizardExtensionsSupport.class)
class EmittedEventResourceTest {
    private static final EmittedEventsBackfillService emittedEventsBackfillService = mock(EmittedEventsBackfillService.class);
    private static final HistoricalEventEmitterService historicalEventEmitterService = mock(HistoricalEventEmitterService.class);

    public static final ResourceExtension resources = ResourceExtension.builder()
            .addResource(new EmittedEventResource(emittedEventsBackfillService, historicalEventEmitterService))
            .build();
    
    @Test
    void shouldReturn200() {
        Response response = resources
                .target("/v1/tasks/emitted-events-sweep")
                .request()
                .post(Entity.json(""));

        assertThat(response.getStatus(), is(Status.OK.getStatusCode()));
    }

    @Test
    void shouldReturn200onHistoricEventById() {
        Response response = resources
                .target("/v1/tasks/historical-event-emitter")
                .queryParam("start_id", 1L)
                .queryParam("max_id", 1L)
                .queryParam("record_type", "charge")
                .queryParam("do_not_retry_emit_until_duration", 1L)
                .request()
                .post(Entity.json(""));

        assertThat(response.getStatus(), is(Status.OK.getStatusCode()));
    }

    @Test
    void shouldReturn200onHistoricEventByIdWithNoRecordType() {
        Response response = resources
                .target("/v1/tasks/historical-event-emitter")
                .queryParam("start_id", 1L)
                .queryParam("max_id", 1L)
                .queryParam("do_not_retry_emit_until_duration", 1L)
                .request()
                .post(Entity.json(""));

        assertThat(response.getStatus(), is(Status.OK.getStatusCode()));
    }

    @Test
    void shouldReturn400onNonStandardRecordTypes() {
        Response response = resources
                .target("/v1/tasks/historical-event-emitter")
                .queryParam("start_id", 1L)
                .queryParam("max_id", 1L)
                .queryParam("record_type", "SOMETHING_ELSE")
                .queryParam("do_not_retry_emit_until_duration", 1L)
                .request()
                .post(Entity.json(""));

        assertThat(response.getStatus(), is(Status.NOT_FOUND.getStatusCode()));
    }

    @Test
    void shouldReturn200OnHistoricEventByDate() {
        Response response = resources
                .target("/v1/tasks/historical-event-emitter-by-date")
                .queryParam("start_date",         ZonedDateTime.now().minusSeconds(2).toString())
                .queryParam("end_date", ZonedDateTime.now().toString())
                .queryParam("do_not_retry_emit_until_duration", 1L)
                .request()
                .post(Entity.json(""));

        assertThat(response.getStatus(), is(Status.OK.getStatusCode()));
    }

}
