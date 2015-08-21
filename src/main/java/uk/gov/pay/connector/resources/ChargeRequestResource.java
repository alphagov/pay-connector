package uk.gov.pay.connector.resources;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.Map;
import java.util.UUID;

import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("/v1/api/charge")
public class ChargeRequestResource {
    private ChargeDao chargeDao;
    private Logger logger = LoggerFactory.getLogger(ChargeRequestResource.class);

    public ChargeRequestResource(ChargeDao chargeDao) {
        this.chargeDao = chargeDao;
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createNewCharge(Map<String, Object> chargeRequest, @Context UriInfo uriInfo) {
        logger.info("Creating new charge of {}.", chargeRequest);
        long chargeId = chargeDao.saveNewCharge(chargeRequest);

        String response = format("{\"charge_id\":\"%s\"}", chargeId);

        URI newLocation = uriInfo.
                getBaseUriBuilder().
                path(ChargeInfoResource.getChargeRoute).build(chargeId);

        return Response.created(newLocation).entity(response).build();
    }
}
