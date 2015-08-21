package uk.gov.pay.connector.resources;

import uk.gov.pay.connector.dao.ChargeDao;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.Map;
import java.util.UUID;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.ok;

@Path("/")
public class ChargeInfoResource {
    public static final String getChargeRoute = "/v1/frontend/charge/{chargeId}";

    private ChargeDao chargeDao;

    public ChargeInfoResource(ChargeDao chargeDao) {
        this.chargeDao = chargeDao;
    }

    @GET
    @Path(getChargeRoute)
    @Produces(APPLICATION_JSON)
    public Response getCharge(@PathParam("chargeId") long chargeId) {
        Map<String, Object> charge = chargeDao.findById(chargeId);
        return ok(removeGatewayAccount(charge)).build();
    }

    private Map<String, Object> removeGatewayAccount(Map<String, Object> charge) {
        charge.remove("gateway_account_id");
        return charge;
    }

}
