package uk.gov.pay.connector.expunge.resource;

import uk.gov.pay.connector.expunge.service.ChargeExpungeService;

import javax.inject.Inject;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.OK;
import static javax.ws.rs.core.Response.status;

@Path("/")
public class ExpungeResource {

    private ChargeExpungeService chargeExpungeService;

    @Inject
    public ExpungeResource(ChargeExpungeService chargeExpungeService) {
        this.chargeExpungeService = chargeExpungeService;
    }

    @POST
    @Path("/v1/tasks/expunge")
    @Produces(APPLICATION_JSON)
    public Response expungeCharges(@QueryParam("number_of_charges_to_expunge") Integer noOfChargesToExpunge) {
        chargeExpungeService.expunge(noOfChargesToExpunge);
        return status(OK).build();
    }
}
