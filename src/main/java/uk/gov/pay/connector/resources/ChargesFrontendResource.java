package uk.gov.pay.connector.resources;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.ok;
import static uk.gov.pay.connector.util.ResponseUtil.responseWithChargeNotFound;

@Path("/")
public class ChargesFrontendResource {
    public static final String FIND_CHARGE_BY_ID = "/v1/frontend/charges/{chargeId}";

    private final Logger logger = LoggerFactory.getLogger(ChargesFrontendResource.class);
    private final ChargeDao chargeDao;

    public ChargesFrontendResource(ChargeDao chargeDao) {
        this.chargeDao = chargeDao;
    }

    @GET
    @Path(FIND_CHARGE_BY_ID)
    @Produces(APPLICATION_JSON)
    public Response getCharge(@PathParam("chargeId") String chargeId, @Context UriInfo uriInfo) {
        Optional<Map<String, Object>> maybeCharge = chargeDao.findById(chargeId);
        return maybeCharge
                .map(charge -> ok(addSelfLink(uriInfo, chargeId, removeGatewayAccount(charge))).build())
                .orElseGet(() -> responseWithChargeNotFound(logger, chargeId));
    }

    private Map<String, Object> addSelfLink(UriInfo uriInfo, String chargeId, Map<String, Object> charge) {
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
