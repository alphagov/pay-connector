package uk.gov.pay.connector.expunge.resource;

import org.slf4j.MDC;
import uk.gov.pay.connector.expunge.service.ExpungeService;

import javax.inject.Inject;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import java.util.UUID;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.OK;
import static javax.ws.rs.core.Response.status;
import static uk.gov.service.payments.logging.LoggingKeys.MDC_REQUEST_ID_KEY;

@Path("/")
public class ExpungeResource {

    private ExpungeService expungeService;

    @Inject
    public ExpungeResource(ExpungeService expungeService) {
        this.expungeService = expungeService;
    }

    @POST
    @Path("/v1/tasks/expunge")
    @Produces(APPLICATION_JSON)
    public Response expunge(@QueryParam("number_of_charges_to_expunge") Integer noOfChargesToExpunge,
                            @QueryParam("number_of_refunds_to_expunge") Integer noOfRefundsToExpunge) {
        String correlationId = MDC.get(MDC_REQUEST_ID_KEY) == null ? "ExpungeResource-" + UUID.randomUUID().toString() : MDC.get(MDC_REQUEST_ID_KEY);
        MDC.put(MDC_REQUEST_ID_KEY, correlationId);
        expungeService.expunge(noOfChargesToExpunge, noOfRefundsToExpunge);
        MDC.remove(MDC_REQUEST_ID_KEY);
        return status(OK).build();
    }
}
