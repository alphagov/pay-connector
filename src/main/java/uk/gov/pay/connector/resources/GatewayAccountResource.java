package uk.gov.pay.connector.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.GatewayAccountDao;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.resources.PaymentProviderValidator.*;
import static uk.gov.pay.connector.util.ResponseUtil.badRequestResponse;
import static uk.gov.pay.connector.util.ResponseUtil.notFoundResponse;

@Path("/v1/api/accounts")
public class GatewayAccountResource {

    private static final Logger logger = LoggerFactory.getLogger(GatewayAccountResource.class);

    private final GatewayAccountDao gatewayDao;

    public GatewayAccountResource(GatewayAccountDao gatewayDao) {
        this.gatewayDao = gatewayDao;
    }

    @GET
    @Path("/{accountId}")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response getGatewayAccount(@PathParam("accountId") String accountId) {

        logger.info("Getting gateway account for account id {}", accountId);

        Optional<Map<String, Object>> accountMaybe = gatewayDao.findById(accountId);
        if (!accountMaybe.isPresent()) {
            return notFoundResponse(logger, format("Account with id %s not found.", accountId));
        }
        return Response.ok().entity(accountMaybe.get()).build();
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createNewGatewayAccount(JsonNode node, @Context UriInfo uriInfo) {

        String provider = node.has(PAYMENT_PROVIDER_KEY) ? node.get(PAYMENT_PROVIDER_KEY).textValue() : DEFAULT_PROVIDER;

        if (!isValidProvider(provider)) {
            return badRequestResponse(logger, format("Unsupported payment provider %s.", provider));
        }

        logger.info("Creating new gateway account using the {} provider", provider);
        String gatewayAccountId = gatewayDao.insertProviderAndReturnNewId(provider);

        URI newLocation = uriInfo.
                getBaseUriBuilder().
                path("/v1/api/accounts/{accountId}").build(gatewayAccountId);

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
