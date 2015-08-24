package uk.gov.pay.connector.resources;

import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.GatewayAccountDao;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.Map;

import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

@Path("/v1/api/charge")
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
        if (!gatewayAccountDao.findNameById(gatewayAccountId).isPresent()) {
            return badGatewayAccount(gatewayAccountId);
        }

        logger.info("Creating new charge of {}.", chargeRequest);
        long chargeId = chargeDao.saveNewCharge(chargeRequest);

        String response = format("{\"charge_id\":\"%s\"}", chargeId);

        URI newLocation = uriInfo.
                getBaseUriBuilder().
                path(ChargeInfoResource.getChargeRoute).build(chargeId);

        return Response.created(newLocation).entity(response).build();
    }

    private Response badGatewayAccount(Long gatewayAccountId) {
        return Response.status(BAD_REQUEST).entity(ImmutableMap.of("message", "Unknown gateway account: " + gatewayAccountId)).build();
    }
}
