package uk.gov.pay.connector.events.resource;

import io.dropwizard.testing.junit.ResourceTestRule;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.events.EmittedEventsBackfillService;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

@RunWith(MockitoJUnitRunner.class)
public class EmittedEventResourceTest {
    private static final EmittedEventsBackfillService emittedEventsBackfillService = mock(EmittedEventsBackfillService.class);
    
    @ClassRule
    public static final ResourceTestRule resources = ResourceTestRule.builder()
            .addResource(new EmittedEventResource(emittedEventsBackfillService))
            .build();

    @Test
    public void shouldReturn200() {
        Response response = resources
                .target("/v1/tasks/emitted-events-sweep")
                .request()
                .post(Entity.json(""));

        assertThat(response.getStatus(), is(Status.OK.getStatusCode()));
    }
}
