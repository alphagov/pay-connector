package uk.gov.pay.connector.resources;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.UUID;

import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("/v1/api/charge")
public class ChargeRequest {
    private ChargeDao chargeDao;
    private Logger logger = LoggerFactory.getLogger(ChargeRequest.class);

    public ChargeRequest(ChargeDao chargeDao) {
        this.chargeDao = chargeDao;
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createNewCharge(JsonNode node, @Context UriInfo uriInfo) {
        long amount = node.get("amount").asLong();

        logger.info("Creating new charge of {}.", amount);
        UUID chargeId = chargeDao.insertAmountAndReturnNewId(amount);

        String response = format("{\"charge_id\":\"%s\"}", chargeId);

        URI newLocation = uriInfo.
                getBaseUriBuilder().
                path(ChargeInfo.getChargeRoute).build(chargeId);

        return Response.created(newLocation).entity(response).build();
    }
}
