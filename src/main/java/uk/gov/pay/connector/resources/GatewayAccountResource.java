package uk.gov.pay.connector.resources;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.GatewayAccountDao;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;

import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("/v1/api/gateway")
public class GatewayAccountResource {

    private static final Logger logger = LoggerFactory.getLogger(GatewayAccountResource.class);

    private final GatewayAccountDao gatewayDao;

    public GatewayAccountResource(GatewayAccountDao gatewayDao) {
        this.gatewayDao = gatewayDao;
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createNewGatewayAccount(JsonNode node, @Context UriInfo uriInfo) {
        logger.error("Testing logging");
        String name = node.get("name").textValue();

        logger.info("Creating new gateway account called {}", name);
        String accountId = gatewayDao.insertNameAndReturnNewId(name);

        String response = format("{\"account_id\":\"%s\"}", accountId);

        URI newLocation = uriInfo.
                getBaseUriBuilder().
                path("/api/gateway/{accountId}").build(accountId);

        return Response.created(newLocation).entity(response).build();
    }

}
