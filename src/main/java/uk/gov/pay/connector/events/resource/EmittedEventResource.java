package uk.gov.pay.connector.events.resource;

import uk.gov.pay.connector.events.EmittedEventsBackfillService;

import javax.inject.Inject;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.OK;
import static javax.ws.rs.core.Response.status;

@Path("/")
public class EmittedEventResource {

    private EmittedEventsBackfillService emittedEventsBackfillService;

    @Inject
    public EmittedEventResource(EmittedEventsBackfillService emittedEventsBackfillService) {
        this.emittedEventsBackfillService = emittedEventsBackfillService;
    }

    @POST
    @Path("/v1/tasks/emitted-events-sweep")
    @Produces(APPLICATION_JSON)
    public Response expireCharges() {
        emittedEventsBackfillService.backfillNotEmittedEvents();
        return status(OK).build();
    }
}
