package uk.gov.pay.connector.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.GatewayAccountDao;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;
import java.util.Map;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.util.ResponseUtil.badResponse;

@Path("/v1/api/accounts")
public class GatewayAccountResource {

    private static final Logger logger = LoggerFactory.getLogger(GatewayAccountResource.class);
    private static final String ACCOUNT_NAME = "name";

    private final GatewayAccountDao gatewayDao;

    public GatewayAccountResource(GatewayAccountDao gatewayDao) {
        this.gatewayDao = gatewayDao;
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createNewGatewayAccount(JsonNode node, @Context UriInfo uriInfo) {
        if (!node.has(ACCOUNT_NAME)) {
            return badResponse(logger, "Missing fields: name");
        }

        String name = node.get(ACCOUNT_NAME).textValue();

        logger.info("Creating new gateway account called {}", name);
        String gatewayAccountId = gatewayDao.insertNameAndReturnNewId(name);

        URI newLocation = uriInfo.
                getBaseUriBuilder().
                path("/api/gateway/{accountId}").build(gatewayAccountId);

        Map<String, Object> account = Maps.newHashMap();
        account.put("gateway_account_id", gatewayAccountId);
        addSelfLink(newLocation, account);

        return Response.created(newLocation).entity(account).build();
    }

    private Map<String, Object> addSelfLink(URI chargeId, Map<String, Object> charge) {
        List<Map<String, Object>> links = ImmutableList.of(ImmutableMap.of("href", chargeId, "rel", "self", "method", "GET"));
        charge.put("links", links);
        return charge;
    }


}
