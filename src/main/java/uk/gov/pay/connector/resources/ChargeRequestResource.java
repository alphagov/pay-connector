package uk.gov.pay.connector.resources;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.util.ResponseUtil;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;
import java.util.Map;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("/v1/api/charges")
public class ChargeRequestResource {
    private ChargeDao chargeDao;
    private GatewayAccountDao gatewayAccountDao;
    private Logger logger = LoggerFactory.getLogger(ChargeRequestResource.class);

    public ChargeRequestResource(ChargeDao chargeDao, GatewayAccountDao gatewayAccountDao) {
        this.chargeDao = chargeDao;
        this.gatewayAccountDao = gatewayAccountDao;
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createNewCharge(Map<String, Object> chargeRequest, @Context UriInfo uriInfo) {

        Long gatewayAccountId = Long.valueOf(chargeRequest.get("gateway_account").toString());
        if (gatewayAccountDao.idIsMissing(gatewayAccountId)) {
            return ResponseUtil.badResponse("Unknown gateway account: " + gatewayAccountId);
        }

        logger.info("Creating new charge of {}.", chargeRequest);
        long chargeId = chargeDao.saveNewCharge(chargeRequest);

        URI newLocation = uriInfo.
                getBaseUriBuilder().
                path(ChargeInfoResource.FIND_CHARGE_BY_ID).build(chargeId);

        Map<String, Object> account = Maps.newHashMap();
        account.put("charge_id", "" + chargeId);
        addSelfLink(newLocation, account);

        return Response.created(newLocation).entity(account).build();
    }

    private Map<String, Object> addSelfLink(URI chargeId, Map<String, Object> charge) {
        List<Map<String, Object>> links = ImmutableList.of(ImmutableMap.of("href", chargeId, "rel", "self", "method", "GET"));
        charge.put("links", links);
        return charge;
    }

}
