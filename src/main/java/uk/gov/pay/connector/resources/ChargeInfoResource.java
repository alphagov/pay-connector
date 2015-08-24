package uk.gov.pay.connector.resources;

import uk.gov.pay.connector.dao.ChargeDao;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.util.Map;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.ok;

@Path("/")
public class ChargeInfoResource {
    public static final String FIND_CHARGE_BY_ID = "/v1/frontend/charges/{chargeId}";

    private ChargeDao chargeDao;

    public ChargeInfoResource(ChargeDao chargeDao) {
        this.chargeDao = chargeDao;
    }

    @GET
    @Path(FIND_CHARGE_BY_ID)
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
