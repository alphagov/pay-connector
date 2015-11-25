package uk.gov.pay.connector.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.GatewayAccountDao;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Joiner.on;
import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.apache.commons.lang3.math.NumberUtils.isNumber;
import static uk.gov.pay.connector.resources.PaymentProviderValidator.DEFAULT_PROVIDER;
import static uk.gov.pay.connector.resources.PaymentProviderValidator.PAYMENT_PROVIDER_KEY;
import static uk.gov.pay.connector.resources.PaymentProviderValidator.isValidProvider;
import static uk.gov.pay.connector.util.ResponseUtil.badRequestResponse;
import static uk.gov.pay.connector.util.ResponseUtil.notFoundResponse;


@Path("/")
public class GatewayAccountResource {

    public static final String ACCOUNTS_API_RESOURCE = "/v1/api/accounts";
    public static final String ACCOUNT_API_RESOURCE = ACCOUNTS_API_RESOURCE + "/{accountId}";

    public static final String ACCOUNTS_FRONTEND_RESOURCE = "/v1/frontend/accounts";
    public static final String ACCOUNT_FRONTEND_RESOURCE = ACCOUNTS_FRONTEND_RESOURCE + "/{accountId}";

    private static final Logger logger = LoggerFactory.getLogger(GatewayAccountResource.class);

    private final GatewayAccountDao gatewayDao;

    public GatewayAccountResource(GatewayAccountDao gatewayDao) {
        this.gatewayDao = gatewayDao;
    }

    @GET
    @Path(ACCOUNT_API_RESOURCE)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response getGatewayAccount(@PathParam("accountId") String accountId) {

        logger.info("Getting gateway account for account id {}", accountId);

        return gatewayDao
                .findById(accountId)
                .map(gatewayAccount -> {
                    gatewayAccount.remove("credentials");
                    return Response.ok().entity(gatewayAccount).build();
                })
                .orElseGet(() -> notFoundResponse(logger, format("Account with id %s not found.", accountId)));

    }

    @POST
    @Path(ACCOUNTS_API_RESOURCE)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createNewGatewayAccount(JsonNode node, @Context UriInfo uriInfo) {

        String provider = node.has(PAYMENT_PROVIDER_KEY) ? node.get(PAYMENT_PROVIDER_KEY).textValue() : DEFAULT_PROVIDER;

        if (!isValidProvider(provider)) {
            return badRequestResponse(logger, format("Unsupported payment provider %s.", provider));
        }

        logger.info("Creating new gateway account using the {} provider", provider);
        String gatewayAccountId = gatewayDao.createGatewayAccount(provider);

        URI newLocation = uriInfo.
                getBaseUriBuilder().
                path("/v1/api/accounts/{accountId}").build(gatewayAccountId);

        Map<String, Object> account = Maps.newHashMap();
        account.put("gateway_account_id", gatewayAccountId);
        addSelfLink(newLocation, account);

        return Response.created(newLocation).entity(account).build();
    }

    @GET
    @Path(ACCOUNT_FRONTEND_RESOURCE)
    @Produces(APPLICATION_JSON)
    public Response getGatewayAccountWithCredentials(@PathParam("accountId") String gatewayAccountId) throws IOException {
        return gatewayDao
                .findById(gatewayAccountId)
                .map(gatewayAccount ->
                {
                    Map<String, String> credentialsMap = (Map<String, String>) gatewayAccount.get("credentials");
                    credentialsMap.remove("password");
                    return Response.ok(gatewayAccount).build();
                })
                .orElseGet(() -> notFoundResponse(logger, format("Account with id '%s' not found", gatewayAccountId)));
    }

    @PUT
    @Path(ACCOUNT_FRONTEND_RESOURCE)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response updateGatewayCredentials(@PathParam("accountId") String gatewayAccountId, JsonNode credentialsPayload) {

        if (!gatewayAccountIdExists(gatewayAccountId)) {
            return notFoundResponse(logger, format("The gateway account id '%s' does not exist", gatewayAccountId));
        }

        List<String> missingFieldsInRequestPayload = getMissingFieldsInRequestPayload(credentialsPayload);
        if (!missingFieldsInRequestPayload.isEmpty()) {
            return badRequestResponse(logger, format("The following fields are missing: [%s]", on(",").join(missingFieldsInRequestPayload)));
        }

        gatewayDao.saveCredentials(credentialsPayload.toString(), gatewayAccountId);
        return Response.ok().build();
    }

    private boolean gatewayAccountIdExists(String gatewayAccountId) {
        if (!isNumber(gatewayAccountId) ||
            !gatewayDao.findById(gatewayAccountId).isPresent()) {
            return false;
        }
        return true;
    }

    private List<String> getMissingFieldsInRequestPayload(JsonNode credentialsPayload) {
        List<String> missingFields = new ArrayList<>();
        if (!credentialsPayload.has("username")) {
            missingFields.add("username");
        }
        if (!credentialsPayload.has("password")) {
            missingFields.add("password");
        }
        return missingFields;
    }

    private Map<String, Object> addSelfLink(URI chargeId, Map<String, Object> charge) {
        List<Map<String, Object>> links = ImmutableList.of(ImmutableMap.of("href", chargeId, "rel", "self", "method", "GET"));
        charge.put("links", links);
        return charge;
    }
}
