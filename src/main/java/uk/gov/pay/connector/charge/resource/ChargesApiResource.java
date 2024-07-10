package uk.gov.pay.connector.charge.resource;

import com.google.inject.persist.Transactional;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.apache.commons.lang3.tuple.Pair;
import org.hibernate.validator.constraints.Length;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.charge.exception.InvalidAttributeValueException;
import uk.gov.pay.connector.charge.exception.MissingMandatoryAttributeException;
import uk.gov.pay.connector.charge.exception.TelephonePaymentNotificationsNotAllowedException;
import uk.gov.pay.connector.charge.exception.UnexpectedAttributeException;
import uk.gov.pay.connector.charge.model.ChargeCreateRequest;
import uk.gov.pay.connector.charge.model.ChargeResponse;
import uk.gov.pay.connector.charge.model.telephone.TelephoneChargeCreateRequest;
import uk.gov.pay.connector.charge.service.ChargeExpiryService;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.charge.validation.ReturnUrlValidator;
import uk.gov.pay.connector.common.model.api.ErrorResponse;
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountNotFoundException;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.usernotification.service.UserNotificationService;
import uk.gov.service.payments.commons.model.AuthorisationMode;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.created;
import static javax.ws.rs.core.Response.ok;
import static uk.gov.pay.connector.util.ResponseUtil.buildErrorResponse;
import static uk.gov.pay.connector.util.ResponseUtil.noContentResponse;
import static uk.gov.pay.connector.util.ResponseUtil.notFoundResponse;
import static uk.gov.pay.connector.util.ResponseUtil.responseWithChargeNotFound;
import static uk.gov.pay.connector.util.ResponseUtil.responseWithGatewayTransactionNotFound;
import static uk.gov.pay.connector.util.ResponseUtil.successResponseWithEntity;

@Path("/")
public class ChargesApiResource {
    public static final String EMAIL_KEY = "email";
    public static final String AMOUNT_KEY = "amount";
    public static final String LANGUAGE_KEY = "language";
    public static final String DELAYED_CAPTURE_KEY = "delayed_capture";
    private static final String DESCRIPTION_KEY = "description";
    private static final String REFERENCE_KEY = "reference";
    public static final Map<String, Integer> MAXIMUM_FIELDS_SIZE = Map.of(
            DESCRIPTION_KEY, 255,
            REFERENCE_KEY, 255,
            EMAIL_KEY, 254
    );
    private static final String ACCOUNT_ID = "accountId";
    private static final String RETURN_URL = "return_url";
    private static final EnumSet<AuthorisationMode> AUTHORISATION_MODES_INCOMPATIBLE_WITH_RETURN_URL = EnumSet.of(AuthorisationMode.MOTO_API, AuthorisationMode.AGREEMENT);
    private static final Logger logger = LoggerFactory.getLogger(ChargesApiResource.class);
    public static final int MIN_AMOUNT = 1;
    public static final int MAX_AMOUNT = 10_000_000;
    private final ChargeService chargeService;
    private final ChargeExpiryService chargeExpiryService;
    private final GatewayAccountService gatewayAccountService;
    private final UserNotificationService userNotificationService;

    @Inject
    public ChargesApiResource(ChargeService chargeService,
                              ChargeExpiryService chargeExpiryService,
                              GatewayAccountService gatewayAccountService,
                              UserNotificationService userNotificationService) {
        this.chargeService = chargeService;
        this.chargeExpiryService = chargeExpiryService;
        this.gatewayAccountService = gatewayAccountService;
        this.userNotificationService = userNotificationService;
    }

    @GET
    @Path("/v1/api/accounts/{accountId}/charges/{chargeId}")
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "Get charge by account ID and charge external ID",
            tags = {"Charges"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(implementation = ChargeResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found")
            }
    )
    public Response getChargeByGatewayAccountId(@Parameter(example = "1", description = "Gateway account ID") @PathParam(ACCOUNT_ID) Long accountId,
                              @Parameter(example = "b02b63b370fd35418ad66b0101", description = "Charge external ID") @PathParam("chargeId") String chargeId,
                              @Context UriInfo uriInfo) {
        return chargeService.findChargeForAccount(chargeId, accountId, uriInfo)
                .map(chargeResponse -> ok(chargeResponse).build())
                .orElseGet(() -> responseWithChargeNotFound(chargeId));
    }

    @GET
    @Path("/v1/api/service/{serviceId}/account/{accountType}/charges/{chargeId}")
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "Get charge by service ID, account type and charge external ID",
            tags = {"Charges"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(implementation = ChargeResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found")
            }
    )
    public Response getChargeByServiceIdAndAccountType(
            @Parameter(example = "46eb1b601348499196c99de90482ee68", description = "Service ID") @PathParam("serviceId") String serviceId, // pragma: allowlist secret
            @Parameter(example = "test", description = "Account type") @PathParam("accountType") GatewayAccountType accountType,
            @Parameter(example = "b02b63b370fd35418ad66b0101", description = "Charge external ID") @PathParam("chargeId") String chargeId,
            @Context UriInfo uriInfo) {
        return chargeService.findChargeForServiceIdAndAccountType(chargeId, serviceId, accountType, uriInfo)
                .map(chargeResponse -> ok(chargeResponse).build())
                .orElseGet(() -> responseWithChargeNotFound(chargeId));
    }

    @POST
    @Path("/v1/api/accounts/{accountId}/charges")
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "Create new charge for gateway account",
            tags = {"Charges"},
            responses = {
                    @ApiResponse(responseCode = "201", description = "Created",
                            content = @Content(schema = @Schema(implementation = ChargeResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Bad Request"),
                    @ApiResponse(responseCode = "403", description = "Gateway account is disabled"),
                    @ApiResponse(responseCode = "404", description = "Not found"),
                    @ApiResponse(responseCode = "422", description = "Missing required fields or invalid values", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    @Transactional
    public Response createNewCharge(
            @Parameter(example = "1", description = "Gateway account ID") @PathParam(ACCOUNT_ID) Long accountId,
            @NotNull @Valid ChargeCreateRequest chargeRequest,
            @Context UriInfo uriInfo,
            @Nullable @Length(min = 1, max = 255, message = "Header [Idempotency-Key] can have a size between 1 and 255") @HeaderParam("Idempotency-Key") String idempotencyKey) {
        logger.info("Creating new charge - {}", chargeRequest.toStringWithoutPersonalIdentifiableInformation());

        AuthorisationMode authorisationMode = chargeRequest.getAuthorisationMode();
        if (authorisationMode == AuthorisationMode.WEB) {
            chargeRequest.getReturnUrl().ifPresentOrElse(returnUrl -> {
                        if (!ReturnUrlValidator.isValid(returnUrl)) {
                            throw new InvalidAttributeValueException(RETURN_URL, "Must be a valid URL format");
                        }
                    },
                    () -> {
                        throw new MissingMandatoryAttributeException(RETURN_URL);
                    });

        } else if (AUTHORISATION_MODES_INCOMPATIBLE_WITH_RETURN_URL.contains(authorisationMode) && chargeRequest.getReturnUrl().isPresent()) {
            throw new UnexpectedAttributeException(RETURN_URL);
        }

        if (idempotencyKey != null && chargeRequest.getAuthorisationMode() == AuthorisationMode.AGREEMENT) {
            Optional<ChargeResponse> maybeExistingChargeResponse = chargeService.checkForChargeCreatedWithIdempotencyKey(
                    chargeRequest, accountId, idempotencyKey, uriInfo);
            if (maybeExistingChargeResponse.isPresent()) {
                ChargeResponse existingChargeResponse = maybeExistingChargeResponse.get();
                return ok(existingChargeResponse.getLink("self")).entity(existingChargeResponse).build();
            }
        }

        return chargeService.create(chargeRequest, accountId, uriInfo, idempotencyKey)
                .map(response -> {
                    if (authorisationMode == AuthorisationMode.AGREEMENT) {
                        chargeService.markChargeAsEligibleForAuthoriseUserNotPresent(response.getChargeId());
                    }
                    return created(response.getLink("self")).entity(response).build();
                })
                .orElseGet(() -> notFoundResponse("Unknown gateway account: " + accountId));
    }

    @POST
    @Path("/v1/api/service/{serviceId}/account/{accountType}/charges")
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "Create new charge for service ID and account type",
            tags = {"Charges"},
            responses = {
                    @ApiResponse(responseCode = "201", description = "Created",
                            content = @Content(schema = @Schema(implementation = ChargeResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Bad Request"),
                    @ApiResponse(responseCode = "403", description = "Gateway account is disabled"),
                    @ApiResponse(responseCode = "404", description = "Not found"),
                    @ApiResponse(responseCode = "422", description = "Missing required fields or invalid values", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    @Transactional
    public Response createNewChargeByServiceIdAndAccountType(
            @Parameter(example = "46eb1b601348499196c99de90482ee68", description = "Service ID") @PathParam("serviceId") String serviceId, // pragma: allowlist secret
            @Parameter(example = "test", description = "Account type") @PathParam("accountType") GatewayAccountType accountType,
            @NotNull @Valid ChargeCreateRequest chargeRequest,
            @Context UriInfo uriInfo,
            @Nullable @Length(min = 1, max = 255, message = "Header [Idempotency-Key] can have a size between 1 and 255") @HeaderParam("Idempotency-Key") String idempotencyKey) {
        logger.info("Creating new charge - {}", chargeRequest.toStringWithoutPersonalIdentifiableInformation());

        AuthorisationMode authorisationMode = chargeRequest.getAuthorisationMode();
        if (authorisationMode == AuthorisationMode.WEB) {
            chargeRequest.getReturnUrl().ifPresentOrElse(returnUrl -> {
                        if (!ReturnUrlValidator.isValid(returnUrl)) {
                            throw new InvalidAttributeValueException(RETURN_URL, "Must be a valid URL format");
                        }
                    },
                    () -> {
                        throw new MissingMandatoryAttributeException(RETURN_URL);
                    });

        } else if (AUTHORISATION_MODES_INCOMPATIBLE_WITH_RETURN_URL.contains(authorisationMode) && chargeRequest.getReturnUrl().isPresent()) {
            throw new UnexpectedAttributeException(RETURN_URL);
        }

        Long accountId = gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(serviceId, accountType)
                .map(GatewayAccountEntity::getId)
                .orElseThrow(() -> new GatewayAccountNotFoundException(serviceId, accountType));

        if (idempotencyKey != null && chargeRequest.getAuthorisationMode() == AuthorisationMode.AGREEMENT) {
            Optional<ChargeResponse> maybeExistingChargeResponse = chargeService.checkForChargeCreatedWithIdempotencyKey(
                    chargeRequest, accountId, idempotencyKey, uriInfo);
            if (maybeExistingChargeResponse.isPresent()) {
                ChargeResponse existingChargeResponse = maybeExistingChargeResponse.get();
                return ok(existingChargeResponse.getLink("self")).entity(existingChargeResponse).build();
            }
        }

        return chargeService.create(chargeRequest, accountId, uriInfo, idempotencyKey)
                .map(response -> {
                    if (authorisationMode == AuthorisationMode.AGREEMENT) {
                        chargeService.markChargeAsEligibleForAuthoriseUserNotPresent(response.getChargeId());
                    }
                    return created(response.getLink("self")).entity(response).build();
                })
                .orElseGet(() -> notFoundResponse("Unknown gateway account: " + accountId));
    }

    @POST
    @Path("/v1/api/accounts/{accountId}/telephone-charges")
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "Create a new telephone charge for gateway account.",
            description = "Create a new telephone charge for gateway account. These are externally taken payments and the outcome is reported to this endpoint. " +
                    "provider_id is used as an idempotency key for API calls. If a payment already exists with the provider_id provided, the API will not store a record about a new payment, or update or change the record about a payment previously stored.",
            tags = {"Charges"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK - returns existing charge for provider_id",
                            content = @Content(schema = @Schema(implementation = ChargeResponse.class))),
                    @ApiResponse(responseCode = "201", description = "Created",
                            content = @Content(schema = @Schema(implementation = ChargeResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(example = "{" +
                            "    \"error_identifier\": \"TELEPHONE_PAYMENT_NOTIFICATIONS_NOT_ALLOWED\"," +
                            "    \"message\": [" +
                            "        \"Telephone payment notifications are not enabled for this gateway account\"" +
                            "    ]" +
                            "}"))),
                    @ApiResponse(responseCode = "404", description = "Not found"),
                    @ApiResponse(responseCode = "422", description = "Missing required fields or invalid values", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public Response createNewTelephoneChargeByAccountId(
            @Parameter(example = "1", description = "Gateway account ID") @PathParam(ACCOUNT_ID) Long accountId,
            @NotNull @Valid TelephoneChargeCreateRequest telephoneChargeCreateRequest,
            @Context UriInfo uriInfo
    ) {
        GatewayAccountEntity gatewayAccount = gatewayAccountService.getGatewayAccount(accountId)
                .orElseThrow(() -> new GatewayAccountNotFoundException(accountId));

        if (!gatewayAccount.isAllowTelephonePaymentNotifications()) {
            throw new TelephonePaymentNotificationsNotAllowedException(gatewayAccount.getId());
        }

        return chargeService.findCharge(accountId, telephoneChargeCreateRequest)
                .map(response -> Response.status(200).entity(response).build())
                .orElseGet(() -> Response.status(201).entity(chargeService.createFromTelephonePaymentNotification(telephoneChargeCreateRequest, gatewayAccount)).build());
    }

    @POST
    @Path("/v1/api/service/{serviceId}/account/{accountType}/telephone-charges")
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "Create a new telephone charge by service id and account type",
            description = "Create a new telephone charge for gateway account. These are externally taken payments and the outcome is reported to this endpoint. " +
                    "provider_id is used as an idempotency key for API calls. If a payment already exists with the provider_id provided, the API will not store a record about a new payment, or update or change the record about a payment previously stored.",
            tags = {"Charges"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK - returns existing charge for provider_id",
                            content = @Content(schema = @Schema(implementation = ChargeResponse.class))),
                    @ApiResponse(responseCode = "201", description = "Created",
                            content = @Content(schema = @Schema(implementation = ChargeResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(example = "{" +
                            "    \"error_identifier\": \"TELEPHONE_PAYMENT_NOTIFICATIONS_NOT_ALLOWED\"," +
                            "    \"message\": [" +
                            "        \"Telephone payment notifications are not enabled for this gateway account\"" +
                            "    ]" +
                            "}"))),
                    @ApiResponse(responseCode = "404", description = "Not found"),
                    @ApiResponse(responseCode = "422", description = "Missing required fields or invalid values", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public Response createNewTelephoneChargeByServiceIdAndAccountType(
            @Parameter(example = "46eb1b601348499196c99de90482ee68", description = "Service ID") @PathParam("serviceId") String serviceId, // pragma: allowlist secret
            @Parameter(example = "test", description = "Account type") @PathParam("accountType") GatewayAccountType accountType,
            @NotNull @Valid TelephoneChargeCreateRequest telephoneChargeCreateRequest,
            @Context UriInfo uriInfo
    ) {
        GatewayAccountEntity gatewayAccount = gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(serviceId, accountType)
                .orElseThrow(() -> new GatewayAccountNotFoundException(serviceId, accountType));

        if (!gatewayAccount.isAllowTelephonePaymentNotifications()) {
            throw new TelephonePaymentNotificationsNotAllowedException(gatewayAccount.getId());
        }

        return chargeService.findCharge(gatewayAccount.getId(), telephoneChargeCreateRequest)
                .map(response -> Response.status(200).entity(response).build())
                .orElseGet(() -> Response.status(201).entity(chargeService.createFromTelephonePaymentNotification(telephoneChargeCreateRequest, gatewayAccount)).build());
    }

    @POST
    @Path("/v1/api/accounts/{accountId}/charges/{chargeId}/resend-confirmation-email")
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "Resend confirmation email for a charge",
            responses = {
                    @ApiResponse(responseCode = "204", description = "No content"),
                    @ApiResponse(responseCode = "402", description = "Could not send email"),
                    @ApiResponse(responseCode = "404", description = "Not found - charge not found"),
                    @ApiResponse(responseCode = "500", description = "Internal server error")
            }
    )
    public Response resendConfirmationEmail(
            @Parameter(example = "1", description = "Gateway account ID")
            @PathParam(ACCOUNT_ID) Long accountId,
            @Parameter(example = "spmh0fb7rbi1lebv1j3f7hc3m9", description = "Charge external ID")
            @PathParam("chargeId") String chargeId) {
        var account = gatewayAccountService.getGatewayAccount(accountId)
                .orElseThrow(() -> new GatewayAccountNotFoundException(accountId));

        return chargeService.findCharge(chargeId, accountId)
                .map(charge -> userNotificationService.sendPaymentConfirmedEmailSynchronously(charge, account, true)
                        .map((reference) -> noContentResponse())
                        .orElseGet(() -> buildErrorResponse(Response.Status.PAYMENT_REQUIRED, "Failed to send email")))
                .orElseGet(() -> responseWithChargeNotFound(chargeId));
    }

    @POST
    @Path("/v1/api/service/{serviceId}/account/{accountType}/charges/{chargeId}/resend-confirmation-email")
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "Resend confirmation email for a charge",
            responses = {
                    @ApiResponse(responseCode = "204", description = "No content"),
                    @ApiResponse(responseCode = "402", description = "Could not send email"),
                    @ApiResponse(responseCode = "404", description = "Not found - charge not found"),
                    @ApiResponse(responseCode = "500", description = "Internal server error")
            }
    )
    public Response resendConfirmationEmailByServiceIdAndAccountType(
            @Parameter(example = "46eb1b601348499196c99de90482ee68", description = "Service ID") // pragma: allowlist secret
            @PathParam("serviceId") String serviceId,
            @Parameter(example = "test", description = "Account type")
            @PathParam("accountType") GatewayAccountType accountType,
            @Parameter(example = "spmh0fb7rbi1lebv1j3f7hc3m9", description = "Charge external ID")
            @PathParam("chargeId") String chargeId) {

        return gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(serviceId, accountType)
                .or(() -> {
                    throw new GatewayAccountNotFoundException(serviceId, accountType);
                })
                .flatMap(account -> chargeService.findCharge(chargeId, account.getId())
                        .map(charge -> Pair.of(charge, account))
                )
                .or(() -> {
                    throw new ChargeNotFoundRuntimeException(chargeId);
                })
                .flatMap(chargeAccountPair -> userNotificationService.sendPaymentConfirmedEmailSynchronously(
                        chargeAccountPair.getLeft(),
                        chargeAccountPair.getRight(),
                        true))
                .map(reference -> noContentResponse())
                .orElseGet(() -> buildErrorResponse(Response.Status.PAYMENT_REQUIRED, "Failed to send email"));
    }

    @POST
    @Path("/v1/tasks/expired-charges-sweep")
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "Expire charges, tokens and idempotency keys",
            description = "This starts a task to expire the charges with a default window of 90 minutes. " +
                    "The default value can be overridden by setting an environment variable CHARGE_EXPIRY_WINDOW_SECONDS in seconds. " +
                    "Response of the call will tell you how many charges were successfully expired and how many of them failed for some reason. " +
                    "This endpoint also expires charges in AWAITING_CAPTURE_REQUEST status. The default window is 120 hours. " +
                    "It can be overriden by setting an environment variable AWAITING_DELAY_CAPTURE_EXPIRY_WINDOW in seconds. " +
                    "Also expires tokens older than the configured TOKEN_EXPIRY_WINDOW_SECONDS, " +
                    "and expires idempotency keys older than the configured IDEMPOTENCY_KEY_EXPIRY_WINDOW_SECONDS.",
            tags = {"Tasks"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(example = "{" +
                                    "    \"expiry-success\": 2," +
                                    "    \"expiry-failed\": 0" +
                                    "}")))
            }
    )
    public Response expireCharges(@Context UriInfo uriInfo) {
        Map<String, Integer> resultMap = chargeExpiryService.sweepAndExpireChargesAndTokensAndIdempotencyKeys();
        return successResponseWithEntity(resultMap);
    }

    @Operation(
            summary = "Find charge by gateway transaction ID",
            tags = {"Charges"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(implementation = ChargeResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found")
            }
    )
    @GET
    @Path("/v1/api/charges/gateway_transaction/{gatewayTransactionId}")
    @Produces(APPLICATION_JSON)
    public Response getChargeForGatewayTransactionId(@Parameter(example = "5422624d-12b1-4821-8b26-d0383ecf1602", description = "Gateway transaction ID")
                                                     @PathParam("gatewayTransactionId") String gatewayTransactionId, @Context UriInfo uriInfo) {
        return chargeService.findChargeByGatewayTransactionId(gatewayTransactionId, uriInfo)
                .map(chargeResponse -> ok(chargeResponse).build())
                .orElseGet(() -> responseWithGatewayTransactionNotFound(gatewayTransactionId));
    }
}
