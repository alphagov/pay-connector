package uk.gov.pay.connector.gatewayaccount.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import com.google.inject.persist.Transactional;
import io.dropwizard.jersey.PATCH;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.cardtype.dao.CardTypeDao;
import uk.gov.pay.connector.cardtype.model.domain.CardTypeEntity;
import uk.gov.pay.connector.charge.exception.ConflictWebApplicationException;
import uk.gov.pay.connector.common.model.api.ErrorResponse;
import uk.gov.pay.connector.common.model.domain.UuidAbstractEntity;
import uk.gov.pay.connector.gatewayaccount.GatewayAccountSwitchPaymentProviderRequest;
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountNotFoundException;
import uk.gov.pay.connector.gatewayaccount.model.CreateGatewayAccountResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountSearchParams;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountWithCredentialsResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountWithCredentialsWithInternalIdResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountsListDTO;
import uk.gov.pay.connector.gatewayaccount.model.Update3dsToggleRequest;
import uk.gov.pay.connector.gatewayaccount.model.UpdateServiceNameRequest;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountServicesFactory;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountSwitchPaymentProviderService;
import uk.gov.service.payments.commons.model.jsonpatch.JsonPatchRequest;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY;
import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static uk.gov.pay.connector.util.ResponseUtil.badRequestResponse;
import static uk.gov.pay.connector.util.ResponseUtil.fieldsInvalidSizeResponse;
import static uk.gov.pay.connector.util.ResponseUtil.fieldsMissingResponse;
import static uk.gov.pay.connector.util.ResponseUtil.notFoundResponse;
import static uk.gov.pay.connector.util.ResponseUtil.successResponseWithEntity;

@Path("/")
public class GatewayAccountResource {

    private static final Logger logger = LoggerFactory.getLogger(GatewayAccountResource.class);
    private static final String SERVICE_NAME_FIELD_NAME = "service_name";
    private static final String REQUIRES_3DS_FIELD_NAME = "toggle_3ds";
    private static final String CARD_TYPES_FIELD_NAME = "card_types";
    private static final int SERVICE_NAME_FIELD_LENGTH = 50;
    private final GatewayAccountService gatewayAccountService;
    private final CardTypeDao cardTypeDao;
    private final GatewayAccountRequestValidator validator;
    private final GatewayAccountServicesFactory gatewayAccountServicesFactory;
    private final GatewayAccountSwitchPaymentProviderService gatewayAccountSwitchPaymentProviderService;

    @Inject
    public GatewayAccountResource(GatewayAccountService gatewayAccountService,
                                  CardTypeDao cardTypeDao,
                                  GatewayAccountRequestValidator validator,
                                  GatewayAccountServicesFactory gatewayAccountServicesFactory,
                                  GatewayAccountSwitchPaymentProviderService gatewayAccountSwitchPaymentProviderService) {
        this.gatewayAccountService = gatewayAccountService;
        this.cardTypeDao = cardTypeDao;
        this.validator = validator;
        this.gatewayAccountServicesFactory = gatewayAccountServicesFactory;
        this.gatewayAccountSwitchPaymentProviderService = gatewayAccountSwitchPaymentProviderService;
    }

    @GET
    @Path("/v1/api/accounts/{accountId}")
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "Find gateway account by ID",
            description = "Get gateway account by internal ID. Returns notifications credentials, gateway account credentials (without password). Doesn't include card_types or gateway_merchant_id",
            tags = {"Gateway accounts"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(implementation = GatewayAccountWithCredentialsWithInternalIdResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found")
            }
    )
    public GatewayAccountWithCredentialsWithInternalIdResponse getGatewayAccount(@Parameter(example = "1", description = "Gateway account ID")
                                                                   @PathParam("accountId") Long gatewayAccountId) {

        return gatewayAccountService.getGatewayAccount(gatewayAccountId)
                .map(GatewayAccountWithCredentialsWithInternalIdResponse::new)
                .orElseThrow(() -> new GatewayAccountNotFoundException(gatewayAccountId));
    }

    @GET
    @Path("/v1/api/service/{serviceId}/account/{accountType}")
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "Find gateway account by service external ID and account type (test|live)",
            description = "Get gateway account by service external ID and account type (test|live). Returns notifications credentials, gateway account credentials (without password). Doesn't include card_types or gateway_merchant_id",
            tags = {"Gateway accounts"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(implementation = GatewayAccountWithCredentialsResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found")
            }
    )
    public GatewayAccountWithCredentialsResponse getGatewayAccountByServiceIdAndAccountType(
            @Parameter(example = "46eb1b601348499196c99de90482ee68", description = "Service ID") @PathParam("serviceId") String serviceId,
            @Parameter(example = "test", description = "Account type") @PathParam("accountType") GatewayAccountType accountType) {
        return gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(serviceId, accountType)
                .map(GatewayAccountWithCredentialsResponse::new)
                .orElseThrow(() -> new GatewayAccountNotFoundException(serviceId, accountType));
    }

    @GET
    @Path("/v1/api/accounts")
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "Search gateway accounts",
            tags = {"Gateway accounts"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(
                                    schema = @Schema(name = "accounts", implementation = GatewayAccountsListDTO.class)
                            ))
            }
    )
    public Response searchGatewayAccounts(
            @Valid @BeanParam
            @Parameter(in = QUERY, schema = @Schema(implementation = GatewayAccountSearchParams.class))
            GatewayAccountSearchParams gatewayAccountSearchParams,
            @Context UriInfo uriInfo) {
        return getGatewayAccounts(gatewayAccountSearchParams, uriInfo);
    }

    private Response getGatewayAccounts(@BeanParam GatewayAccountSearchParams gatewayAccountSearchParams, @Context UriInfo uriInfo) {
        logger.info(format("Searching gateway accounts by parameters %s", gatewayAccountSearchParams.toString()));

        List<GatewayAccountResponse> gatewayAccounts = gatewayAccountService.searchGatewayAccounts(gatewayAccountSearchParams);
        gatewayAccounts.forEach(account -> account.addLink("self", buildUri(uriInfo, account.getAccountId())));

        return Response
                .ok(GatewayAccountsListDTO.of(gatewayAccounts))
                .build();
    }

    private URI buildUri(UriInfo uriInfo, long accountId) {
        return uriInfo.getBaseUriBuilder()
                .path("/v1/api/accounts/{accountId}")
                .build(accountId);
    }

    @GET
    @Path("/v1/frontend/accounts/external-id/{externalId}")
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "Find gateway account by gateway account external ID",
            description = "Get gateway account by external ID. Also returns notifications credentials, gateway account credentials (without password)",
            tags = {"Gateway accounts"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(name = "accounts", implementation = GatewayAccountWithCredentialsWithInternalIdResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found")
            }
    )
    public GatewayAccountWithCredentialsWithInternalIdResponse getFrontendGatewayAccountByExternalId(@PathParam("externalId") String externalId) {
        return gatewayAccountService
                .getGatewayAccountByExternal(externalId)
                .map(GatewayAccountWithCredentialsWithInternalIdResponse::new)
                .orElseThrow(() -> new GatewayAccountNotFoundException(format("Account with external id %s not found.", externalId)));
    }

    @GET
    @Path("/v1/frontend/accounts/{accountId}/card-types")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "Get card types for gateway account",
            tags = {"Gateway accounts"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(example = "{" +
                                    "    \"card_types\": [" +
                                    "        {" +
                                    "            \"id\": \"ab8a3abd-bcfd-4fa6-8905-321ce913e7f5\"," +
                                    "            \"brand\": \"visa\"," +
                                    "            \"label\": \"Visa\"," +
                                    "            \"type\": \"DEBIT\"," +
                                    "            \"requires3ds\": false" +
                                    "        }" +
                                    "    ]" +
                                    "}"))),
                    @ApiResponse(responseCode = "404", description = "Not found")
            }
    )
    public Response getGatewayAccountAcceptedCardTypes(@Parameter(example = "1", description = "Gateway account ID") @PathParam("accountId") Long accountId) {
        logger.info("Getting accepted card types for gateway account with account id {}", accountId);
        return gatewayAccountService.getGatewayAccount(accountId)
                .map(gatewayAccount -> successResponseWithEntity(ImmutableMap.of(CARD_TYPES_FIELD_NAME, gatewayAccount.getCardTypes())))
                .orElseGet(() -> notFoundResponse(format("Account with id %s not found.", accountId)));
    }

    @GET
    @Path("/v1/frontend/service/{serviceId}/account/{accountType}/card-types")
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "Get card types for gateway account by service external ID and account type",
            tags = {"Gateway accounts"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(example = "{" +
                                    "    \"card_types\": [" +
                                    "        {" +
                                    "            \"id\": \"ab8a3abd-bcfd-4fa6-8905-321ce913e7f5\"," +
                                    "            \"brand\": \"visa\"," +
                                    "            \"label\": \"Visa\"," +
                                    "            \"type\": \"DEBIT\"," +
                                    "            \"requires3ds\": false" +
                                    "        }" +
                                    "    ]" +
                                    "}"))),
                    @ApiResponse(responseCode = "404", description = "Not found")
            }
    )
    public Response getAcceptedCardTypesByServiceIdAndAccountType(
            @Parameter(example = "46eb1b601348499196c99de90482ee68", description = "Service ID") @PathParam("serviceId") String serviceId,
            @Parameter(example = "test", description = "Account type") @PathParam("accountType") GatewayAccountType accountType) {
        logger.info("Getting accepted card types for service id {}, account type {}", serviceId, accountType.toString());
        return gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(serviceId, accountType)
                .map(gatewayAccount -> successResponseWithEntity(Map.of(CARD_TYPES_FIELD_NAME, gatewayAccount.getCardTypes())))
                .orElseThrow(() -> new GatewayAccountNotFoundException(serviceId, accountType));
    }

    @POST
    @Path("/v1/api/accounts")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Valid
    @Operation(
            summary = "Create a new gateway account ",
            tags = {"Gateway accounts"},
            responses = {
                    @ApiResponse(responseCode = "201", description = "Created",
                            content = @Content(schema = @Schema(name = "accounts", implementation = CreateGatewayAccountResponse.class))),
                    @ApiResponse(responseCode = "422", description = "Missing required fields or invalid values",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public Response createNewGatewayAccount(@Valid @NotNull GatewayAccountRequest gatewayAccountRequest,
                                            @Context UriInfo uriInfo,
                                            @QueryParam("degatewayification") boolean degatewayification) {

        logger.info("Creating new gateway account using the {} provider pointing to {}",
                gatewayAccountRequest.getPaymentProvider(),
                gatewayAccountRequest.getProviderAccountType());

        if (degatewayification) {
            GatewayAccountType accountType = GatewayAccountType.fromString(gatewayAccountRequest.getProviderAccountType());
            String serviceId = gatewayAccountRequest.getServiceId();
            var existingGatewayAccount = gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(serviceId, accountType);
            if (existingGatewayAccount.isPresent()) {
                throw new ConflictWebApplicationException(String.format("Gateway account with service id %s and account type '%s' already exists.", serviceId, accountType));
            }
        }
        CreateGatewayAccountResponse createGatewayAccountResponse = gatewayAccountService.createGatewayAccount(gatewayAccountRequest, uriInfo);

        return Response.created(createGatewayAccountResponse.getLocation()).entity(createGatewayAccountResponse).build();
    }

    @PATCH
    @Path("/v1/api/service/{serviceId}/account/{accountType}")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    @Operation(
            summary = "Patch a gateway account ",
            description = "A generic endpoint that allows the patching of allow_apple_pay, allow_google_pay, block_prepaid_cards, notify_settings, " +
                    "email_collection_mode, corporate_credit_card_surcharge_amount, corporate_debit_card_surcharge_amount, " +
                    "corporate_prepaid_debit_card_surcharge_amount, allow_zero_amount, allow_moto, moto_mask_card_number_input, " +
                    "moto_mask_card_security_code_input, allow_telephone_payment_notifications, send_payer_ip_address_to_gateway, send_payer_email_to_gateway, " +
                    "integration_version_3ds, send_reference_to_gateway, allow_authorisation_api or worldpay_exemption_engine_enabled using a JSON Patch-esque message body.",
            tags = {"Gateway accounts"},
            requestBody = @RequestBody(content = @Content(schema = @Schema(example = "{" +
                    "    \"op\":\"replace\", \"path\":\"allow_apple_pay\", \"value\": true" +
                    "}"))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found")
            }
    )
    public Response patchGatewayAccountByServiceIdAndType(
            @Parameter(example = "46eb1b601348499196c99de90482ee68", description = "Service ID") @PathParam("serviceId") String serviceId,
            @Parameter(example = "test", description = "Account type") @PathParam("accountType") GatewayAccountType accountType,
            JsonNode payload) {

        validator.validatePatchRequest(payload);
        
        return gatewayAccountServicesFactory.getUpdateService()
                .doPatch(serviceId, accountType, JsonPatchRequest.from(payload))
                .map(gatewayAccount -> Response.ok().build())
                .orElseGet(() -> Response.status(NOT_FOUND).build());
    }
    
    @PATCH
    @Path("/v1/api/accounts/{accountId}")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    @Operation(
            summary = "Patch a gateway account ",
            description = "A generic endpoint that allows the patching of allow_apple_pay, allow_google_pay, block_prepaid_cards, notify_settings, " +
                    "email_collection_mode, corporate_credit_card_surcharge_amount, corporate_debit_card_surcharge_amount, " +
                    "corporate_prepaid_debit_card_surcharge_amount, allow_zero_amount, allow_moto, moto_mask_card_number_input, " +
                    "moto_mask_card_security_code_input, allow_telephone_payment_notifications, send_payer_ip_address_to_gateway, send_payer_email_to_gateway, " +
                    "integration_version_3ds, send_reference_to_gateway, allow_authorisation_api or worldpay_exemption_engine_enabled using a JSON Patch-esque message body.",
            tags = {"Gateway accounts"},
            requestBody = @RequestBody(content = @Content(schema = @Schema(example = "{" +
                    "    \"op\":\"replace\", \"path\":\"allow_apple_pay\", \"value\": true" +
                    "}"))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found")
            }
    )
    public Response patchGatewayAccountByGatewayAccountId(
            @Parameter(example = "1", description = "Gateway account ID") @PathParam("accountId") Long gatewayAccountId, 
            JsonNode payload) {
        validator.validatePatchRequest(payload);

        return gatewayAccountServicesFactory.getUpdateService()
                .doPatch(gatewayAccountId, JsonPatchRequest.from(payload))
                .map(gatewayAccount -> Response.ok().build())
                .orElseGet(() -> Response.status(NOT_FOUND).build());
    }

    @PATCH
    @Path("/v1/frontend/service/{serviceId}/account/{accountType}/servicename")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    @Operation(
            summary = "Update service name of a gateway account",
            tags = {"Gateway accounts"},
            requestBody = @RequestBody(content = @Content(schema = @Schema(example = "{" +
                    "  \"service_name\": \"a new service name\"" +
                    "}", requiredProperties = {"service_name"}))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found"),
            }
    )
    public Response updateGatewayAccountServiceNameByServiceId(
            @Parameter(example = "46eb1b601348499196c99de90482ee68", description = "Service ID") @PathParam("serviceId") String serviceId,
            @Parameter(example = "test", description = "Account type") @PathParam("accountType") GatewayAccountType accountType,
            @Valid UpdateServiceNameRequest updateServiceNameRequest) {

        return gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(serviceId, accountType)
                .map(gatewayAccount ->
                        {
                            gatewayAccount.setServiceName(updateServiceNameRequest.getServiceName());
                            return Response.ok().build();
                        }
                )
                .orElseThrow(() -> new GatewayAccountNotFoundException(serviceId, accountType));
    }
    
    @PATCH
    @Path("/v1/frontend/accounts/{accountId}/servicename")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    @Operation(
            summary = "Update service name of a gateway account",
            tags = {"Gateway accounts"},
            requestBody = @RequestBody(content = @Content(schema = @Schema(example = "{" +
                    "  \"service_name\": \"a new service name\"" +
                    "}", requiredProperties = {"service_name"}))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found"),
            }
    )
    public Response updateGatewayAccountServiceNameByGatewayAccountId(@Parameter(example = "1", description = "Gateway account ID") @PathParam("accountId") Long gatewayAccountId,
                                                    Map<String, String> payload) {
        if (!payload.containsKey(SERVICE_NAME_FIELD_NAME)) {
            return fieldsMissingResponse(Collections.singletonList(SERVICE_NAME_FIELD_NAME));
        }

        String serviceName = payload.get(SERVICE_NAME_FIELD_NAME);
        if (serviceName.length() > SERVICE_NAME_FIELD_LENGTH) {
            return fieldsInvalidSizeResponse(Collections.singletonList(SERVICE_NAME_FIELD_NAME));
        }

        return gatewayAccountService.getGatewayAccount(gatewayAccountId)
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
    @Path("/v1/frontend/service/{serviceId}/account/{accountType}/3ds-toggle")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    @Operation(
            summary = "Set requires3ds flag on a gateway account",
            tags = {"Gateway accounts"},
            requestBody = @RequestBody(content = @Content(schema = @Schema(example = "{" +
                    "  \"toggle_3ds\": \"true\"" +
                    "}"))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found"),
                    @ApiResponse(responseCode = "409", description = "Conflict - 3ds cannot be disabled for account")
            }
    )
    public Response updateGatewayAccount3dsToggleByServiceId(
            @Parameter(example = "46eb1b601348499196c99de90482ee68", description = "Service ID") @PathParam("serviceId") String serviceId,
            @Parameter(example = "test", description = "Account type") @PathParam("accountType") GatewayAccountType accountType,
            Update3dsToggleRequest update3dsToggleRequest) {
        
        return gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(serviceId, accountType)
                .map(gatewayAccount ->
                        {
                            if (!update3dsToggleRequest.isToggle3ds() && gatewayAccount.hasAnyAcceptedCardType3dsRequired()) {
                                return Response.status(Status.CONFLICT).build();
                            }
                            gatewayAccount.setRequires3ds(update3dsToggleRequest.isToggle3ds());
                            return Response.ok().build();
                        }
                )
                .orElseThrow(() -> new GatewayAccountNotFoundException(serviceId, accountType));
    }
    
    @PATCH
    @Path("/v1/frontend/accounts/{accountId}/3ds-toggle")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    @Operation(
            summary = "Set requires3ds flag on a gateway account",
            tags = {"Gateway accounts"},
            requestBody = @RequestBody(content = @Content(schema = @Schema(example = "{" +
                    "  \"toggle_3ds\": \"true\"" +
                    "}"))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found"),
                    @ApiResponse(responseCode = "409", description = "Conflict - 3ds cannot be disabled for account")
            }
    )
    public Response updateGatewayAccount3dsToggleByGatewayAccountId(@Parameter(example = "1", description = "Gateway account ID") @PathParam("accountId") Long gatewayAccountId,
                                                                    Map<String, String> gatewayAccountPayload) {
        if (!gatewayAccountPayload.containsKey(REQUIRES_3DS_FIELD_NAME)) {
            return fieldsMissingResponse(Collections.singletonList(REQUIRES_3DS_FIELD_NAME));
        }

        return gatewayAccountService.getGatewayAccount(gatewayAccountId)
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
    @Operation(
            summary = "Update accepted card types for a gateway account",
            tags = {"Gateway accounts"},
            requestBody = @RequestBody(content = @Content(schema = @Schema(example = "{" +
                    "    \"card_types\": [" +
                    "        \"ab8a3abd-bcfd-4fa6-8905-321ce913e7f5\"," +
                    "        \"3863fc6a-6425-49cb-b708-af76296bcfc1\"" +
                    "    ]" +
                    "}", requiredProperties = {"card_types"}))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found"),
                    @ApiResponse(responseCode = "409", description = "Conflict - requires3DS is false on gateway account but atleast one card type requires 3DS to be enabled. ")
            }
    )
    public Response updateGatewayAccountAcceptedCardTypesByGatewayAccountId(
            @Parameter(example = "1", description = "Gateway account ID") @PathParam("accountId") Long gatewayAccountId,
            Map<String, List<UUID>> cardTypes) {

        if (!cardTypes.containsKey(CARD_TYPES_FIELD_NAME)) {
            return fieldsMissingResponse(Collections.singletonList(CARD_TYPES_FIELD_NAME));
        }

        List<UUID> cardTypeIds = cardTypes.get(CARD_TYPES_FIELD_NAME);

        return gatewayAccountService.getGatewayAccount(gatewayAccountId)
                .map(gatewayAccountEntity -> updateGatewayAccountAcceptedCardTypes(cardTypeIds, gatewayAccountEntity))
                .orElseGet(() -> notFoundResponse(format("The gateway account id '%s' does not exist", gatewayAccountId)));
    }

    @POST
    @Path("/v1/frontend/service/{serviceId}/account/{accountType}/card-types")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    @Operation(
            summary = "Update accepted card types for a gateway account",
            tags = {"Gateway accounts"},
            requestBody = @RequestBody(content = @Content(schema = @Schema(example = "{" +
                    "    \"card_types\": [" +
                    "        \"ab8a3abd-bcfd-4fa6-8905-321ce913e7f5\"," +
                    "        \"3863fc6a-6425-49cb-b708-af76296bcfc1\"" +
                    "    ]" +
                    "}", requiredProperties = {"card_types"}))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found"),
                    @ApiResponse(responseCode = "409", description = "Conflict - requires3DS is false on gateway account but atleast one card type requires 3DS to be enabled. ")
            }
    )
    public Response updateGatewayAccountAcceptedCardTypesByServiceId(
            @Parameter(example = "1", description = "Service ID") @PathParam("serviceId") String serviceId,
            @Parameter(example = "test", description = "Account type") @PathParam("accountType") GatewayAccountType accountType,
            Map<String, List<UUID>> cardTypes) {

        if (!cardTypes.containsKey(CARD_TYPES_FIELD_NAME)) {
            return fieldsMissingResponse(Collections.singletonList(CARD_TYPES_FIELD_NAME));
        }

        List<UUID> cardTypeIds = cardTypes.get(CARD_TYPES_FIELD_NAME);

        return gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(serviceId, accountType)
                .map(gatewayAccountEntity -> updateGatewayAccountAcceptedCardTypes(cardTypeIds, gatewayAccountEntity))
                .orElseThrow(() -> new GatewayAccountNotFoundException(serviceId, accountType));
    }

    private Response updateGatewayAccountAcceptedCardTypes(List<UUID> cardTypeIds, GatewayAccountEntity gatewayAccountToEdit) {
        List<CardTypeEntity> cardTypeEntities = cardTypeIds.stream()
                .map(cardTypeDao::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        if (cardTypeIds.size() != cardTypeEntities.size()) {
            String errorMessage = format("Accepted Card Type(s) referenced by id(s) '%s' not found", String.join(",", extractNotFoundCardTypeIds(cardTypeIds, cardTypeEntities)));
            logger.error(errorMessage);
            return badRequestResponse(errorMessage);
        }

        if (!gatewayAccountToEdit.isRequires3ds() && hasAnyRequired3ds(cardTypeEntities)) {
            return Response.status(Status.CONFLICT).build();
        }
        gatewayAccountToEdit.setCardTypes(cardTypeEntities);
        return Response.ok().build();
    }

    private boolean hasAnyRequired3ds(List<CardTypeEntity> cardTypeEntities) {
        return cardTypeEntities.stream().anyMatch(CardTypeEntity::isRequires3ds);
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
    @Path("/v1/api/accounts/{accountId}/switch-psp")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "Switch payment provider of a gateway account",
            tags = {"Gateway accounts"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found")
            }
    )
    public Response switchPaymentProvider(@Parameter(example = "1", description = "Gateway account ID")
                                          @PathParam("accountId") Long gatewayAccountId, @NotNull GatewayAccountSwitchPaymentProviderRequest request) {
        GatewayAccountSwitchPaymentProviderRequestValidator.validate(request);

        return gatewayAccountService.getGatewayAccount(gatewayAccountId)
                .map(gatewayAccountEntity -> {
                    if (!gatewayAccountEntity.isProviderSwitchEnabled()) {
                        return badRequestResponse("Account is not configured to switch PSP or already switched PSP.");
                    }
                    try {
                        gatewayAccountSwitchPaymentProviderService.switchPaymentProviderForAccount(gatewayAccountEntity, request);
                    } catch (BadRequestException ex) {
                        logger.error("Switching Payment Provider failure: {}", ex.getMessage());
                        return badRequestResponse(ex.getMessage());
                    } catch (NotFoundException ex) {
                        logger.error("Switching Payment Provider failure: {}", ex.getMessage());
                        return notFoundResponse(ex.getMessage());
                    }
                    return Response.ok().build();
                })
                .orElseGet(() -> notFoundResponse(format("The gateway account id [%s] does not exist.", gatewayAccountId)));
    }
}
