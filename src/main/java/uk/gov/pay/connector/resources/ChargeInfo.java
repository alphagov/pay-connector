package uk.gov.pay.connector.resources;

import uk.gov.pay.connector.dao.ChargeDao;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.ok;

@Path("/")
public class ChargeInfo {
    public static final String getChargeRoute = "/v1/frontend/charge/{chargeId}";

    private ChargeDao chargeDao;

    public ChargeInfo(ChargeDao chargeDao) {
        this.chargeDao = chargeDao;
    }

    @GET
    @Path(getChargeRoute)
    @Produces(APPLICATION_JSON)
    public Response getCharge(@PathParam("chargeId") String chargeId) {
        long amount = chargeDao.getAmountById(chargeId);

        String response = format("{\"amount\": %s}", amount);

        return ok(response).build();
    }
}
