package uk.gov.pay.connector.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.dao.GatewayAccountJpaDao;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;

import javax.inject.Inject;
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

import static com.google.common.collect.Maps.newHashMap;
import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.resources.PaymentProviderValidator.*;
import static uk.gov.pay.connector.util.ResponseUtil.badRequestResponse;

@Path("/")
public class GatewayAccountJpaResource {
    public static final String ACCOUNTS_API_RESOURCE = "/v1/api/accounts";
    public static final String ACCOUNT_API_RESOURCE = ACCOUNTS_API_RESOURCE + "/{accountId}";

    public static final String ACCOUNTS_FRONTEND_RESOURCE = "/v1/frontend/accounts";
    public static final String ACCOUNT_FRONTEND_RESOURCE = ACCOUNTS_FRONTEND_RESOURCE + "/{accountId}";
    private static final Logger logger = LoggerFactory.getLogger(GatewayAccountResource.class);


    private final GatewayAccountJpaDao gatewayDao;
    private final Map<String, List<String>> providerCredentialFields;

    @Inject
    public GatewayAccountJpaResource(GatewayAccountJpaDao gatewayDao, ConnectorConfiguration conf) {
        this.gatewayDao = gatewayDao;
        providerCredentialFields = newHashMap();
        providerCredentialFields.put("worldpay", conf.getWorldpayConfig().getCredentials());
        providerCredentialFields.put("smartpay", conf.getSmartpayConfig().getCredentials());
    }

    //    @GET
//    @Path(ACCOUNT_API_RESOURCE)
//    @Consumes(APPLICATION_JSON)
//    @Produces(APPLICATION_JSON)
//    public Response getGatewayAccount(@PathParam("accountId") String accountId) {
//
//        logger.info("Getting gateway account for account id {}", accountId);
//
//        return gatewayDao
//                .findById(accountId)
//                .map(gatewayAccount -> Response.ok().entity(gatewayAccount.withoutCredentials()).build())
//                .orElseGet(() -> notFoundResponse(logger, format("Account with id %s not found.", accountId)));
//
//    }
//
    @POST
    @Path(ACCOUNTS_API_RESOURCE + "/jpa")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createNewGatewayAccount(JsonNode node, @Context UriInfo uriInfo) {

        String provider = node.has(PAYMENT_PROVIDER_KEY) ? node.get(PAYMENT_PROVIDER_KEY).textValue() : DEFAULT_PROVIDER;

        if (!isValidProvider(provider)) {
            return badRequestResponse(logger, format("Unsupported payment provider %s.", provider));
        }

        logger.info("Creating new gateway account using the {} provider", provider);
//        GatewayAccountEntity entity = new GatewayAccountEntity(provider, newHashMap());
        GatewayAccountEntity entity = new GatewayAccountEntity(provider, "{}");
        gatewayDao.persist(entity);

        URI newLocation = uriInfo.
                getBaseUriBuilder().
                path("/v1/api/accounts/{accountId}").build(entity.getId());

        Map<String, Object> account = newHashMap();
        account.put("gateway_account_id", String.valueOf(entity.getId()));
        addSelfLink(newLocation, account);

        return Response.created(newLocation).entity(account).build();
    }
//
//    @GET
//    @Path(ACCOUNT_FRONTEND_RESOURCE)
//    @Produces(APPLICATION_JSON)
//    public Response getGatewayAccountWithCredentials(@PathParam("accountId") String gatewayAccountId) throws IOException {
//        return gatewayDao
//                .findById(gatewayAccountId)
//                .map(serviceAccount ->
//                {
//                    serviceAccount.getCredentials().remove("password");
//                    return Response.ok(serviceAccount).build();
//                })
//                .orElseGet(() -> notFoundResponse(logger, format("Account with id '%s' not found", gatewayAccountId)));
//    }
//
//    @PUT
//    @Path(ACCOUNT_FRONTEND_RESOURCE)
//    @Consumes(APPLICATION_JSON)
//    @Produces(APPLICATION_JSON)
//    public Response updateGatewayCredentials(@PathParam("accountId") String gatewayAccountId, JsonNode credentialsPayload) {
//
//        if (!isNumber(gatewayAccountId)) {
//            return notFoundResponse(logger, format("The gateway account id '%s' does not exist", gatewayAccountId));
//        }
//
//        return gatewayDao.findById(gatewayAccountId)
//                .map(  gatewayAccount ->
//                        {
//                            List<String> missingFieldsInRequestPayload = getMissingFieldsInRequestPayload(credentialsPayload, gatewayAccount.getGatewayName());
//                            if (!missingFieldsInRequestPayload.isEmpty()) {
//                                return badRequestResponse(logger, format("The following fields are missing: [%s]", on(", ").join(missingFieldsInRequestPayload)));
//                            }
//
//                            gatewayDao.saveCredentials(credentialsPayload.toString(), gatewayAccountId);
//                            return Response.ok().build();
//                        }
//                )
//                .orElseGet(() ->
//                        notFoundResponse(logger, format("The gateway account id '%s' does not exist", gatewayAccountId)));
//    }
//
//    private List<String> getMissingFieldsInRequestPayload(JsonNode credentialsPayload, String provider) {
//        return providerCredentialFields.get(provider).stream()
//                .filter(requiredField -> !credentialsPayload.has(requiredField))
//                .collect(Collectors.toList());
//    }

    private Map<String, Object> addSelfLink(URI chargeId, Map<String, Object> charge) {
        List<Map<String, Object>> links = ImmutableList.of(ImmutableMap.of("href", chargeId, "rel", "self", "method", "GET"));
        charge.put("links", links);
        return charge;
    }
}
