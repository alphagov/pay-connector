package uk.gov.pay.connector.report.resource;

import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.gov.pay.connector.report.ParityCheckerService;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;

@ExtendWith(DropwizardExtensionsSupport.class)
class ParityCheckerResourceTest {
    
    private static final ParityCheckerService parityCheckerService = mock(ParityCheckerService.class);

    public static final ResourceExtension resources = ResourceExtension.builder()
            .addResource(new ParityCheckerResource(parityCheckerService))
            .build();

    @Test
    void parityCheckCharge() {
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
    void parityCheckRefunds() {
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

    @Test
    void parityCheckRefundsWorksWithNoRecordType() {
        Response response = resources
                .target("/v1/tasks/parity-checker")
                .queryParam("start_id", 1L)
                .queryParam("max_id", 1L)
                .queryParam("do_not_reprocess_valid_records", true)
                .queryParam("do_not_retry_emit_until", 1L)
                .request()
                .post(Entity.json(""));

        assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
    }


    @Test
    void parityCheckRefundsFailsWithNonStandardRecordType() {
        Response response = resources
                .target("/v1/tasks/parity-checker")
                .queryParam("start_id", 1L)
                .queryParam("max_id", 1L)
                .queryParam("do_not_reprocess_valid_records", true)
                .queryParam("do_not_retry_emit_until", 1L)
                .queryParam("record_type", "SOMETHING_ELSE")
                .request()
                .post(Entity.json(""));

        assertThat(response.getStatus(), is(Response.Status.NOT_FOUND.getStatusCode()));
    }

}
