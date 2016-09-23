package uk.gov.pay.connector.resources;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.persist.Transactional;
import io.dropwizard.jersey.PATCH;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.dao.CardTypeDao;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.exception.CredentialsException;
import uk.gov.pay.connector.model.domain.CardTypeEntity;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.service.GatewayAccountNotificationCredentialsService;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.collect.Maps.newHashMap;
import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.model.domain.GatewayAccountEntity.Type;
import static uk.gov.pay.connector.model.domain.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.resources.ApiPaths.*;
import static uk.gov.pay.connector.util.ResponseUtil.*;

@Path("/")
public class GatewayAccountResource {

    private static final Logger logger = LoggerFactory.getLogger(GatewayAccountResource.class);

    private static final String DESCRIPTION_FIELD_NAME = "description";
    private static final String ANALYTICS_ID_FIELD_NAME = "analytics_id";
    private static final String CREDENTIALS_FIELD_NAME = "credentials";
    private static final String SERVICE_NAME_FIELD_NAME = "service_name";
    private static final String CARD_TYPES_FIELD_NAME = "card_types";
    private static final int SERVICE_NAME_FIELD_LENGTH = 50;
    private static final String PROVIDER_ACCOUNT_TYPE = "type";
    private static final String PAYMENT_PROVIDER_KEY = "payment_provider";
    private static final String USERNAME_KEY = "username";
    private static final String PASSWORD_KEY = "password";


    private final GatewayAccountDao gatewayDao;
    private final CardTypeDao cardTypeDao;
    private final Map<String, List<String>> providerCredentialFields;
    private final GatewayAccountNotificationCredentialsService gatewayAccountNotificationCredentialsService;


    @Inject
    public GatewayAccountResource(GatewayAccountDao gatewayDao, CardTypeDao cardTypeDao, ConnectorConfiguration conf,
                                  GatewayAccountNotificationCredentialsService gatewayAccountNotificationCredentialsService) {
        this.gatewayDao = gatewayDao;
        this.cardTypeDao = cardTypeDao;
        this.gatewayAccountNotificationCredentialsService = gatewayAccountNotificationCredentialsService;
        providerCredentialFields = newHashMap();
        providerCredentialFields.put("worldpay", conf.getWorldpayConfig().getCredentials());
        providerCredentialFields.put("smartpay", conf.getSmartpayConfig().getCredentials());
    }

    @GET
    @Path(GATEWAY_ACCOUNT_API_PATH)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @JsonView(GatewayAccountEntity.Views.FullView.class)
    public Response getGatewayAccount(@PathParam("accountId") Long accountId) {
        logger.info("Getting gateway account for account id {}", accountId);
        return gatewayDao
                .findById(GatewayAccountEntity.class, accountId)
                .map(gatewayAccount -> Response.ok().entity(gatewayAccount.withoutCredentials()).build())
                .orElseGet(() -> notFoundResponse(format("Account with id %s not found.", accountId)));

    }

    @GET
    @Path(FRONTEND_GATEWAY_ACCOUNT_API_PATH)
    @Produces(APPLICATION_JSON)
    @JsonView(GatewayAccountEntity.Views.FullView.class)
    public Response getGatewayAccountWithCredentials(@PathParam("accountId") Long gatewayAccountId) throws IOException {

        return gatewayDao.findById(gatewayAccountId)
                .map(serviceAccount ->
                {
                    serviceAccount.getCredentials().remove("password");
                    return Response.ok(serviceAccount).build();
                })
                .orElseGet(() -> notFoundResponse(format("Account with id '%s' not found", gatewayAccountId)));
    }

    @GET
    @Path(FRONTEND_ACCOUNT_CARDTYPES_API_PATH)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @JsonView(GatewayAccountEntity.Views.FullView.class)
    public Response getGatewayAccountAcceptedCardTypes(@PathParam("accountId") Long accountId) {
        logger.info("Getting accepted card types for gateway account with account id {}", accountId);
        return gatewayDao
                .findById(GatewayAccountEntity.class, accountId)
                .map(gatewayAccount -> successResponseWithEntity(ImmutableMap.of(CARD_TYPES_FIELD_NAME, gatewayAccount.getCardTypes())))
                .orElseGet(() -> notFoundResponse(format("Account with id %s not found.", accountId)));

    }

    @POST
    @Path(GATEWAY_ACCOUNTS_API_PATH)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createNewGatewayAccount(JsonNode node, @Context UriInfo uriInfo) {
        String accountType = node.has(PROVIDER_ACCOUNT_TYPE) ? node.get(PROVIDER_ACCOUNT_TYPE).textValue() : TEST.toString();
        Type type;
        try {
            type = GatewayAccountEntity.Type.fromString(accountType);
        } catch (IllegalArgumentException iae) {
            return badRequestResponse(format("Unsupported payment provider account type '%s', should be one of (test, live)", accountType));
        }

        String provider = node.has(PAYMENT_PROVIDER_KEY) ? node.get(PAYMENT_PROVIDER_KEY).textValue() : PaymentGatewayName.SANDBOX.getName();

        if (!PaymentGatewayName.isValidPaymentGateway(provider)) {
            return badRequestResponse(format("Unsupported payment provider %s.", provider));
        }

        logger.info("Creating new gateway account using the {} provider pointing to {}", provider, accountType);
        GatewayAccountEntity entity = new GatewayAccountEntity(provider, newHashMap(), type);
        logger.info("Setting the new account to accept all card types by default", provider, accountType);
        entity.setCardTypes(cardTypeDao.findAll());
        if (node.has(DESCRIPTION_FIELD_NAME)) {
            entity.setDescription(node.get(DESCRIPTION_FIELD_NAME).textValue());
        }
        if (node.has(ANALYTICS_ID_FIELD_NAME)) {
            entity.setAnalyticsId(node.get(ANALYTICS_ID_FIELD_NAME).textValue());
        }
        gatewayDao.persist(entity);
        URI newLocation = uriInfo.
                getBaseUriBuilder().
                path("/v1/api/accounts/{accountId}").build(entity.getId());

        Map<String, Object> account = newHashMap();
        account.put("gateway_account_id", String.valueOf(entity.getId()));
        account.put(PROVIDER_ACCOUNT_TYPE, entity.getType());
        account.put(DESCRIPTION_FIELD_NAME, entity.getDescription());
        account.put(ANALYTICS_ID_FIELD_NAME, entity.getAnalyticsId());

        addSelfLink(newLocation, account);

        return Response.created(newLocation).entity(account).build();
    }

    @PATCH
    @Path(FRONTEND_ACCOUNT_CREDENTIALS_API_PATH)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    @JsonView(GatewayAccountEntity.Views.FullView.class)
    public Response updateGatewayAccountCredentials(@PathParam("accountId") Long gatewayAccountId, Map<String, Object> gatewayAccountPayload) {
        if (!gatewayAccountPayload.containsKey(CREDENTIALS_FIELD_NAME)) {
            return fieldsMissingResponse(Arrays.asList(CREDENTIALS_FIELD_NAME));
        }

        return gatewayDao.findById(gatewayAccountId)
                .map(gatewayAccount ->
                        {
                            Map credentialsPayload = (Map) gatewayAccountPayload.get(CREDENTIALS_FIELD_NAME);
                            List<String> missingCredentialsFields = checkMissingCredentialsFields(credentialsPayload, gatewayAccount.getGatewayName());
                            if (!missingCredentialsFields.isEmpty()) {
                                return fieldsMissingResponse(missingCredentialsFields);
                            }

                            gatewayAccount.setCredentials(new ObjectMapper().convertValue(credentialsPayload, Map.class));
                            return Response.ok().build();
                        }
                )
                .orElseGet(() ->
                        notFoundResponse(format("The gateway account id '%s' does not exist", gatewayAccountId)));
    }

    @PATCH
    @Path(FRONTEND_ACCOUNT_SERVICENAME_API_PATH)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    public Response updateGatewayAccountServiceName(@PathParam("accountId") Long gatewayAccountId, Map<String, String> gatewayAccountPayload) {
        if (!gatewayAccountPayload.containsKey(SERVICE_NAME_FIELD_NAME)) {
            return fieldsMissingResponse(Arrays.asList(SERVICE_NAME_FIELD_NAME));
        }

        String serviceName = gatewayAccountPayload.get(SERVICE_NAME_FIELD_NAME);
        if (serviceName.length() > SERVICE_NAME_FIELD_LENGTH) {
            return fieldsInvalidSizeResponse(Arrays.asList(SERVICE_NAME_FIELD_NAME));
        }

        return gatewayDao.findById(gatewayAccountId)
                .map(gatewayAccount ->
                        {
                            gatewayAccount.setServiceName(serviceName);
                            return Response.ok().build();
                        }
                )
                .orElseGet(() ->
                        notFoundResponse(format("The gateway account id '%s' does not exist", gatewayAccountId)));
    }

    @POST
    @Path(FRONTEND_ACCOUNT_CARDTYPES_API_PATH)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    public Response updateGatewayAccountAcceptedCardTypes(@PathParam("accountId") Long gatewayAccountId, Map<String, List<UUID>> cardTypes) {
        if (!cardTypes.containsKey(CARD_TYPES_FIELD_NAME)) {
            return fieldsMissingResponse(Arrays.asList(CARD_TYPES_FIELD_NAME));
        }

        List<UUID> cardTypeIds = cardTypes.get(CARD_TYPES_FIELD_NAME);

        List<String> cardTypeIdNotFound = cardTypeIds.stream()
                .filter(cardTypeId -> !cardTypeDao.findById(cardTypeId).isPresent())
                .map(UUID::toString)
                .collect(Collectors.toList());

        if (cardTypeIdNotFound.size() > 0) {
            return badRequestResponse(format("CardType(s) referenced by id(s) '%s' not found", String.join(",", cardTypeIdNotFound)));
        }

        List<CardTypeEntity> cardTypeEntities = cardTypeIds.stream()
                .map(CardTypeEntity::aCardType)
                .collect(Collectors.toList());

        return gatewayDao.findById(gatewayAccountId)
                .map(gatewayAccount -> {
                    gatewayAccount.setCardTypes(cardTypeEntities);
                    return Response.ok().build();
                })
                .orElseGet(() ->
                        notFoundResponse(format("The gateway account id '%s' does not exist", gatewayAccountId)));
    }

    @POST
    @Path(GATEWAY_ACCOUNTS_NOTIFICATION_CREDENTIALS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    public Response createOrUpdateGatewayAccountNotificationCredentials(@PathParam("accountId") Long gatewayAccountId, Map<String,String> notificationCredentials) {
        if (!notificationCredentials.containsKey(USERNAME_KEY)) {
            return fieldsMissingResponse(Collections.singletonList(USERNAME_KEY));
        }

        if (!notificationCredentials.containsKey(PASSWORD_KEY)) {
            return fieldsMissingResponse(Collections.singletonList(PASSWORD_KEY));
        }

        return gatewayDao.findById(gatewayAccountId)
                .map((gatewayAccountEntity) -> {
                    try {
                        gatewayAccountNotificationCredentialsService.setCredentialsForAccount(notificationCredentials,
                                gatewayAccountEntity);
                    } catch (CredentialsException e) {
                        return badRequestResponse("Credentials update failure: "+ e.getMessage());
                    }

                    return Response.ok().build();

                })
                .orElseGet(() -> notFoundResponse(format("The gateway account id '%s' does not exist", gatewayAccountId)));
    }


    private Map<String, Object> addSelfLink(URI chargeId, Map<String, Object> charge) {
        List<Map<String, Object>> links = ImmutableList.of(ImmutableMap.of("href", chargeId, "rel", "self", "method", "GET"));
        charge.put("links", links);
        return charge;
    }

    private List<String> checkMissingCredentialsFields(Map<String, Object> credentialsPayload, String provider) {
        return providerCredentialFields.get(provider).stream()
                .filter(requiredField -> !credentialsPayload.containsKey(requiredField))
                .collect(Collectors.toList());
    }
}
