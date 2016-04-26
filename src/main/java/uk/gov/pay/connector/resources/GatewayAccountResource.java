package uk.gov.pay.connector.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.google.common.collect.Maps.newHashMap;
import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static jersey.repackaged.com.google.common.base.Joiner.on;
import static uk.gov.pay.connector.resources.ApiPaths.*;
import static uk.gov.pay.connector.resources.PaymentProviderValidator.*;
import static uk.gov.pay.connector.util.ResponseUtil.badRequestResponse;
import static uk.gov.pay.connector.util.ResponseUtil.notFoundResponse;

@Path("/")
public class GatewayAccountResource {

    private static final Logger logger = LoggerFactory.getLogger(GatewayAccountResource.class);

    private final GatewayAccountDao gatewayDao;
    private final Map<String, List<String>> providerCredentialFields;

    @Inject
    public GatewayAccountResource(GatewayAccountDao gatewayDao, ConnectorConfiguration conf) {
        this.gatewayDao = gatewayDao;
        providerCredentialFields = newHashMap();
        providerCredentialFields.put("worldpay", conf.getWorldpayConfig().getCredentials());
        providerCredentialFields.put("smartpay", conf.getSmartpayConfig().getCredentials());
    }

    @GET
    @Path(GATEWAY_ACCOUNT_API_PATH)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response getGatewayAccount(@PathParam("accountId") Long accountId) {
        logger.info("Getting gateway account for account id {}", accountId);
        return gatewayDao
                .findById(GatewayAccountEntity.class, accountId)
                .map(gatewayAccount -> Response.ok().entity(gatewayAccount.withoutCredentials()).build())
                .orElseGet(() -> notFoundResponse(format("Account with id %s not found.", accountId)));

    }

    @POST
    @Path(GATEWAY_ACCOUNTS_API_PATH)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createNewGatewayAccount(JsonNode node, @Context UriInfo uriInfo) {

        String provider = node.has(PAYMENT_PROVIDER_KEY) ? node.get(PAYMENT_PROVIDER_KEY).textValue() : DEFAULT_PROVIDER;

        if (!isValidProvider(provider)) {
            return badRequestResponse(format("Unsupported payment provider %s.", provider));
        }

        logger.info("Creating new gateway account using the {} provider", provider);
        GatewayAccountEntity entity = new GatewayAccountEntity(provider, newHashMap());
        gatewayDao.persist(entity);

        URI newLocation = uriInfo.
                getBaseUriBuilder().
                path("/v1/api/accounts/{accountId}").build(entity.getId());

        Map<String, Object> account = newHashMap();
        account.put("gateway_account_id", String.valueOf(entity.getId()));
        addSelfLink(newLocation, account);

        return Response.created(newLocation).entity(account).build();
    }

    @GET
    @Path(FRONTEND_GATEWAY_ACCOUNT_API_PATH)
    @Produces(APPLICATION_JSON)
    public Response getGatewayAccountWithCredentials(@PathParam("accountId") Long gatewayAccountId) throws IOException {

        return gatewayDao.findById(gatewayAccountId)
                .map(serviceAccount ->
                {
                    serviceAccount.getCredentials().remove("password");
                    return Response.ok(serviceAccount).build();
                })
                .orElseGet(() -> notFoundResponse(format("Account with id '%s' not found", gatewayAccountId)));
    }

    @PUT
    @Path(FRONTEND_GATEWAY_ACCOUNT_API_PATH)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    public Response updateGatewayCredentials(@PathParam("accountId") Long gatewayAccountId, JsonNode credentialsPayload) {

        return gatewayDao.findById(gatewayAccountId)
                .map(gatewayAccount ->
                        {
                            List<String> missingFieldsInRequestPayload = getMissingFieldsInRequestPayload(credentialsPayload, gatewayAccount.getGatewayName());
                            if (!missingFieldsInRequestPayload.isEmpty()) {
                                return badRequestResponse(format("The following fields are missing: [%s]", on(", ").join(missingFieldsInRequestPayload)));
                            }
                            gatewayAccount.setCredentials(new ObjectMapper().convertValue(credentialsPayload, Map.class));
                            gatewayDao.persist(gatewayAccount);
                            return Response.ok().build();
                        }
                )
                .orElseGet(() ->
                        notFoundResponse(format("The gateway account id '%s' does not exist", gatewayAccountId)));
    }

    private List<String> getMissingFieldsInRequestPayload(JsonNode credentialsPayload, String provider) {
        return providerCredentialFields.get(provider).stream()
                .filter(requiredField -> !credentialsPayload.has(requiredField))
                .collect(Collectors.toList());
    }

    private Map<String, Object> addSelfLink(URI chargeId, Map<String, Object> charge) {
        List<Map<String, Object>> links = ImmutableList.of(ImmutableMap.of("href", chargeId, "rel", "self", "method", "GET"));
        charge.put("links", links);
        return charge;
    }
}
