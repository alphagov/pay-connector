package uk.gov.pay.connector.resources;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Splitter;
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
import uk.gov.pay.connector.model.PatchRequest;
import uk.gov.pay.connector.model.domain.CardTypeEntity;
import uk.gov.pay.connector.model.domain.EmailNotificationEntity;
import uk.gov.pay.connector.model.domain.EmailNotificationType;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.GatewayAccountResourceDTO;
import uk.gov.pay.connector.model.domain.UuidAbstractEntity;
import uk.gov.pay.connector.service.GatewayAccountNotificationCredentialsService;
import uk.gov.pay.connector.service.GatewayAccountServicesFactory;
import uk.gov.pay.connector.service.PaymentGatewayName;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.google.common.collect.Maps.newHashMap;
import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static uk.gov.pay.connector.model.domain.GatewayAccountEntity.Type;
import static uk.gov.pay.connector.model.domain.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.util.ResponseUtil.badRequestResponse;
import static uk.gov.pay.connector.util.ResponseUtil.fieldsInvalidSizeResponse;
import static uk.gov.pay.connector.util.ResponseUtil.fieldsMissingResponse;
import static uk.gov.pay.connector.util.ResponseUtil.notFoundResponse;
import static uk.gov.pay.connector.util.ResponseUtil.successResponseWithEntity;

@Path("/")
public class GatewayAccountResource {

    private static final Logger logger = LoggerFactory.getLogger(GatewayAccountResource.class);
    private static final Splitter COMMA_SEPARATOR = Splitter.on(',').trimResults().omitEmptyStrings();

    private static final String DESCRIPTION_FIELD_NAME = "description";
    private static final String ANALYTICS_ID_FIELD_NAME = "analytics_id";
    private static final String CREDENTIALS_FIELD_NAME = "credentials";
    private static final String SERVICE_NAME_FIELD_NAME = "service_name";
    private static final String REQUIRES_3DS_FIELD_NAME = "toggle_3ds";
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
    private final GatewayAccountRequestValidator validator;
    private final GatewayAccountServicesFactory gatewayAccountServicesFactory;


    @Inject
    public GatewayAccountResource(GatewayAccountDao gatewayDao, CardTypeDao cardTypeDao, ConnectorConfiguration conf,
                                  GatewayAccountNotificationCredentialsService gatewayAccountNotificationCredentialsService,
                                  GatewayAccountRequestValidator validator, GatewayAccountServicesFactory gatewayAccountServicesFactory) {
        this.gatewayDao = gatewayDao;
        this.cardTypeDao = cardTypeDao;
        this.gatewayAccountNotificationCredentialsService = gatewayAccountNotificationCredentialsService;
        this.validator = validator;
        this.gatewayAccountServicesFactory = gatewayAccountServicesFactory;
        providerCredentialFields = newHashMap();
        providerCredentialFields.put("worldpay", conf.getWorldpayConfig().getCredentials());
        providerCredentialFields.put("smartpay", conf.getSmartpayConfig().getCredentials());
        providerCredentialFields.put("epdq", conf.getEpdqConfig().getCredentials());
    }

    @GET
    @Path("/v1/api/accounts/{accountId}")
    @Produces(APPLICATION_JSON)
    @JsonView(GatewayAccountEntity.Views.ApiView.class)
    public Response getGatewayAccount(@PathParam("accountId") Long accountId) {
        logger.debug("Getting gateway account for account id {}", accountId);
        return gatewayDao
                .findById(GatewayAccountEntity.class, accountId)
                .map(gatewayAccount -> Response.ok().entity(gatewayAccount.withoutCredentials()).build())
                .orElseGet(() -> notFoundResponse(format("Account with id %s not found.", accountId)));

    }

    // This private method, instead of using a regex Path is due to, as far as
    // I can tell, the binding of @POST causing /v1/api/accounts to 405, thus
    // we have to explicitly define /v1/api/accounts with @GET
    // As such, split out the common functionality in a private method and call
    // it twice for each explicitly defined resource
    private Response getGatewayAccounts(
      String accountIdsArg,
      UriInfo uriInfo
    ) {
      logger.debug("Parsing {} to filter all gateway accounts.", accountIdsArg);
      List<Long> accountIds;

      try{
        accountIds = COMMA_SEPARATOR.splitToList(accountIdsArg)
          .stream()
          .map(Long::parseLong)
          .collect(Collectors.toList());
      } catch (NumberFormatException e) {
        return badRequestResponse(format("Could not parse accountIds %s as Longs.", accountIdsArg));
      }

      List<GatewayAccountResourceDTO> gatewayAccountResourceDTOList;

      if (accountIds.isEmpty()) {
        gatewayAccountResourceDTOList = gatewayDao.listAll();
      } else {
        gatewayAccountResourceDTOList = gatewayDao.list(accountIds);
      }

      logger.debug("Getting gateway accounts {}.", accountIdsArg);

      gatewayAccountResourceDTOList.forEach(account -> {
        account.addLink("self", buildUri(uriInfo, account.getAccountId()));
      });

      return Response
        .ok(ImmutableMap.of("accounts", gatewayAccountResourceDTOList))
        .build();
    }

    @GET
    @Path("/v1/api/accounts")
    @Produces(APPLICATION_JSON)
    public Response getApiGatewayAccounts(
        @DefaultValue("") @QueryParam("accountIds") String accountIdsArg,
        @Context UriInfo uriInfo
    ) {
      return getGatewayAccounts(accountIdsArg, uriInfo);
    }

    @GET
    @Path("/v1/frontend/accounts")
    @Produces(APPLICATION_JSON)
    public Response getFrontendGatewayAccounts(
        @DefaultValue("") @QueryParam("accountIds") String accountIdsArg,
        @Context UriInfo uriInfo
    ) {
      return getGatewayAccounts(accountIdsArg, uriInfo);
    }

    private URI buildUri(UriInfo uriInfo, long accountId) {
        return uriInfo.getBaseUriBuilder()
                .path("/v1/api/accounts/{accountId}")
                .build(accountId);
    }

    @GET
    @Path("/v1/frontend/accounts/{accountId}")
    @Produces(APPLICATION_JSON)
    @JsonView(GatewayAccountEntity.Views.ApiView.class)
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
    @Path("/v1/frontend/accounts/{accountId}/card-types")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @JsonView(GatewayAccountEntity.Views.ApiView.class)
    public Response getGatewayAccountAcceptedCardTypes(@PathParam("accountId") Long accountId) {
        logger.info("Getting accepted card types for gateway account with account id {}", accountId);
        return gatewayDao
                .findById(GatewayAccountEntity.class, accountId)
                .map(gatewayAccount -> successResponseWithEntity(ImmutableMap.of(CARD_TYPES_FIELD_NAME, gatewayAccount.getCardTypes())))
                .orElseGet(() -> notFoundResponse(format("Account with id %s not found.", accountId)));

    }

    @POST
    @Path("/v1/api/accounts")
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
        entity.setCardTypes(cardTypeDao.findAllNon3ds());
        if (node.has(SERVICE_NAME_FIELD_NAME)) {
            entity.setServiceName(node.get(SERVICE_NAME_FIELD_NAME).textValue());
        }
        if (node.has(DESCRIPTION_FIELD_NAME)) {
            entity.setDescription(node.get(DESCRIPTION_FIELD_NAME).textValue());
        }
        if (node.has(ANALYTICS_ID_FIELD_NAME)) {
            entity.setAnalyticsId(node.get(ANALYTICS_ID_FIELD_NAME).textValue());
        }
        
        entity.addNotification(EmailNotificationType.PAYMENT_CONFIRMED, new EmailNotificationEntity(entity));
        entity.addNotification(EmailNotificationType.REFUND_ISSUED, new EmailNotificationEntity(entity));
        gatewayDao.persist(entity);
        URI newLocation = uriInfo.
                getBaseUriBuilder().
                path("/v1/api/accounts/{accountId}").build(entity.getId());

        Map<String, Object> account = newHashMap();
        account.put("gateway_account_id", String.valueOf(entity.getId()));
        account.put(PROVIDER_ACCOUNT_TYPE, entity.getType());
        account.put(DESCRIPTION_FIELD_NAME, entity.getDescription());
        account.put(SERVICE_NAME_FIELD_NAME, entity.getServiceName());
        account.put(ANALYTICS_ID_FIELD_NAME, entity.getAnalyticsId());

        addSelfLink(newLocation, account);

        return Response.created(newLocation).entity(account).build();
    }

    @PATCH
    @Path("/v1/api/accounts/{accountId}")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    public Response patchGatewayAccount(@PathParam("accountId") Long gatewayAccountId, JsonNode payload) { 
        validator.validatePatchRequest(payload);
        
        return gatewayAccountServicesFactory.getUpdateService()
                        .doPatch(gatewayAccountId, PatchRequest.from(payload))
                        .map(gatewayAccount -> Response.ok().build())
                        .orElseGet(() -> Response.status(NOT_FOUND).build());
    }

    @PATCH
    @Path("/v1/frontend/accounts/{accountId}/credentials")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    @JsonView(GatewayAccountEntity.Views.ApiView.class)
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
    @Path("/v1/frontend/accounts/{accountId}/servicename")
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

    @PATCH
    @Path("/v1/frontend/accounts/{accountId}/3ds-toggle")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    public Response updateGatewayAccount3dsToggle(@PathParam("accountId") Long gatewayAccountId, Map<String, String> gatewayAccountPayload) {
        if (!gatewayAccountPayload.containsKey(REQUIRES_3DS_FIELD_NAME)) {
            return fieldsMissingResponse(Arrays.asList(REQUIRES_3DS_FIELD_NAME));
        }

        return gatewayDao.findById(gatewayAccountId)
                .map(gatewayAccount ->
                        {
                            boolean requires3ds = Boolean.parseBoolean(gatewayAccountPayload.get(REQUIRES_3DS_FIELD_NAME));
                            if (!requires3ds && gatewayAccount.hasAnyAcceptedCardType3dsRequired()) {
                                return Response.status(Status.CONFLICT).build();
                            }
                            gatewayAccount.setRequires3ds(requires3ds);
                            return Response.ok().build();
                        }
                )
                .orElseGet(() ->
                        notFoundResponse(format("The gateway account id '%s' does not exist", gatewayAccountId)));
    }

    @POST
    @Path("/v1/frontend/accounts/{accountId}/card-types")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    public Response updateGatewayAccountAcceptedCardTypes(@PathParam("accountId") Long gatewayAccountId, Map<String, List<UUID>> cardTypes) {

        if (!cardTypes.containsKey(CARD_TYPES_FIELD_NAME)) {
            return fieldsMissingResponse(Arrays.asList(CARD_TYPES_FIELD_NAME));
        }

        List<UUID> cardTypeIds = cardTypes.get(CARD_TYPES_FIELD_NAME);

        List<CardTypeEntity> cardTypeEntities = cardTypeIds.stream()
                .map(cardTypeDao::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        if (cardTypeIds.size() != cardTypeEntities.size()) {
            return badRequestResponse(format("CardType(s) referenced by id(s) '%s' not found", String.join(",", extractNotFoundCardTypeIds(cardTypeIds, cardTypeEntities))));
        }

        return gatewayDao.findById(gatewayAccountId)
                .map(gatewayAccount -> {
                    if (!gatewayAccount.isRequires3ds() && hasAnyRequired3ds(cardTypeEntities)) {
                        return Response.status(Status.CONFLICT).build();
                    }
                    gatewayAccount.setCardTypes(cardTypeEntities);
                    return Response.ok().build();
                })
                .orElseGet(() ->
                        notFoundResponse(format("The gateway account id '%s' does not exist", gatewayAccountId)));
    }

    private boolean hasAnyRequired3ds(List<CardTypeEntity> cardTypeEntities) {
        return cardTypeEntities.stream()
                .filter(CardTypeEntity::isRequires3ds)
                .count() > 0;
    }

    private List<String> extractNotFoundCardTypeIds(List<UUID> cardTypeIds, List<CardTypeEntity> cardTypeEntities) {
        List<UUID> foundIds = cardTypeEntities.stream()
                .map(UuidAbstractEntity::getId)
                .collect(Collectors.toList());

        return cardTypeIds.stream()
                .filter(cardTypeId -> !foundIds.contains(cardTypeId))
                .map(UUID::toString)
                .collect(Collectors.toList());
    }

    @POST
    @Path("/v1/api/accounts/{accountId}/notification-credentials")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    public Response createOrUpdateGatewayAccountNotificationCredentials(@PathParam("accountId") Long gatewayAccountId, Map<String, String> notificationCredentials) {
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
                        return badRequestResponse("Credentials update failure: " + e.getMessage());
                    }

                    return Response.ok().build();

                })
                .orElseGet(() -> notFoundResponse(format("The gateway account id '%s' does not exist", gatewayAccountId)));
    }

    @PATCH
    @Path("/v1/api/accounts/{accountId}/description-analytics-id")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    public Response updateDescriptionAndOrAnalyticsID(@PathParam("accountId") Long gatewayAccountId, Map<String, String> payload) {
        if (!payload.containsKey(DESCRIPTION_FIELD_NAME) && !payload.containsKey(ANALYTICS_ID_FIELD_NAME)) {
            return fieldsMissingResponse(Arrays.asList(DESCRIPTION_FIELD_NAME, ANALYTICS_ID_FIELD_NAME));
        }
        Optional<String> descriptionMaybe = Optional.ofNullable(payload.get(DESCRIPTION_FIELD_NAME));
        Optional<String> analyticsIdMaybe = Optional.ofNullable(payload.get(ANALYTICS_ID_FIELD_NAME));
        return gatewayDao.findById(gatewayAccountId)
                .map((gatewayAccountEntity) -> {
                    descriptionMaybe.ifPresent(gatewayAccountEntity::setDescription);
                    analyticsIdMaybe.ifPresent(gatewayAccountEntity::setAnalyticsId);
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
