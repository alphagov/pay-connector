package uk.gov.pay.connector.resources;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import uk.gov.pay.connector.dao.ChargeDao;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;
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
    public Response getCharge(@PathParam("chargeId") long chargeId, @Context UriInfo uriInfo) {
        Map<String, Object> charge = chargeDao.findById(chargeId);
        return ok(addSelfLink(uriInfo, chargeId, removeGatewayAccount(charge))).build();
    }

    private Map<String, Object> addSelfLink(UriInfo uriInfo, long chargeId, Map<String, Object> charge) {
        URI selfUri = uriInfo.getAbsolutePathBuilder().build(chargeId);
        List<Map<String, Object>> links = ImmutableList.of(ImmutableMap.of("href", selfUri, "rel", "self", "method", "GET"));
        charge.put("links", links);
        return charge;
    }

    private Map<String, Object> removeGatewayAccount(Map<String, Object> charge) {
        charge.remove("gateway_account_id");
        return charge;
    }

}
