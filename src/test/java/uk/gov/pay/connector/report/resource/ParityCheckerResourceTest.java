package uk.gov.pay.connector.report.resource;

import io.dropwizard.testing.junit.ResourceTestRule;
import org.junit.ClassRule;
import org.junit.Test;
import uk.gov.pay.connector.events.EmittedEventsBackfillService;
import uk.gov.pay.connector.events.HistoricalEventEmitterService;
import uk.gov.pay.connector.events.resource.EmittedEventResource;
import uk.gov.pay.connector.report.ParityCheckerService;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

public class ParityCheckerResourceTest {
    
    private static final ParityCheckerService parityCheckerService = mock(ParityCheckerService.class);

    @ClassRule
    public static final ResourceTestRule resources = ResourceTestRule.builder()
            .addResource(new ParityCheckerResource(parityCheckerService))
            .build();


    @Test
    public void parityCheckCharge() {
        Response response = resources
                .target("/v1/tasks/parity-checker")
                .queryParam("start_id", 1L)
                .queryParam("max_id", 1L)
                .queryParam("do_not_reprocess_valid_records", true)
                .queryParam("do_not_retry_emit_until", 1L)
                .queryParam("record_type", "CHARGE")
                .request()
                .post(Entity.json(""));

        assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
    }

    @Test
    public void parityCheckRefunds() {
        Response response = resources
                .target("/v1/tasks/parity-checker")
                .queryParam("start_id", 1L)
                .queryParam("max_id", 1L)
                .queryParam("do_not_reprocess_valid_records", true)
                .queryParam("do_not_retry_emit_until", 1L)
                .queryParam("record_type", "REFUND")
                .request()
                .post(Entity.json(""));

        assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
    }

}
