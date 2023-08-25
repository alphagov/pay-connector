package uk.gov.pay.connector.paymentprocessor.resource;

import com.google.common.collect.ImmutableMap;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.charge.exception.motoapi.AuthorisationErrorException;
import uk.gov.pay.connector.charge.exception.motoapi.AuthorisationRejectedException;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.service.ChargeCancelService;
import uk.gov.pay.connector.charge.service.ChargeEligibleForCaptureService;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.charge.service.DelayedCaptureService;
import uk.gov.pay.connector.charge.service.motoapi.MotoApiCardNumberValidationService;
import uk.gov.pay.connector.client.cardid.model.CardInformation;
import uk.gov.pay.connector.common.model.api.ErrorResponse;
import uk.gov.pay.connector.gateway.model.Auth3dsResult;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse.AuthoriseStatus;
import uk.gov.pay.connector.gateway.model.response.Gateway3DSAuthorisationResponse;
import uk.gov.pay.connector.paymentprocessor.api.AuthorisationResponse;
import uk.gov.pay.connector.paymentprocessor.model.AuthoriseRequest;
import uk.gov.pay.connector.paymentprocessor.service.Card3dsResponseAuthService;
import uk.gov.pay.connector.paymentprocessor.service.CardAuthoriseService;
import uk.gov.pay.connector.token.TokenService;
import uk.gov.pay.connector.token.model.domain.TokenEntity;
import uk.gov.pay.connector.util.MDCUtils;
import uk.gov.pay.connector.util.ResponseUtil;
import uk.gov.pay.connector.wallets.applepay.ApplePayService;
import uk.gov.pay.connector.wallets.applepay.api.ApplePayAuthRequest;
import uk.gov.pay.connector.wallets.googlepay.GooglePayService;
import uk.gov.pay.connector.wallets.googlepay.api.GooglePayAuthRequest;
import uk.gov.pay.connector.wallets.googlepay.api.StripeGooglePayAuthRequest;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.Map;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.util.ResponseUtil.badRequestResponse;
import static uk.gov.pay.connector.util.ResponseUtil.gatewayErrorResponse;
import static uk.gov.service.payments.logging.LoggingKeys.SECURE_TOKEN;

@Path("/")
@Tag(name = "Charge operations")
public class CardResource {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final CardAuthoriseService cardAuthoriseService;
    private final Card3dsResponseAuthService card3dsResponseAuthService;
    private final ChargeEligibleForCaptureService chargeEligibleForCaptureService;
    private final DelayedCaptureService delayedCaptureService;
    private final ChargeCancelService chargeCancelService;
    private final ApplePayService applePayService;
    private final GooglePayService googlePayService;
    private final TokenService tokenService;
    private final MotoApiCardNumberValidationService motoApiCardNumberValidationService;
    private final ChargeService chargeService;

    @Inject
    public CardResource(CardAuthoriseService cardAuthoriseService, Card3dsResponseAuthService card3dsResponseAuthService,
                        ChargeEligibleForCaptureService chargeEligibleForCaptureService, DelayedCaptureService delayedCaptureService,
                        ChargeCancelService chargeCancelService, ApplePayService applePayService, GooglePayService googlePayService,
                        TokenService tokenService, MotoApiCardNumberValidationService motoApiCardNumberValidationService,
                        ChargeService chargeService) {
        this.cardAuthoriseService = cardAuthoriseService;
        this.card3dsResponseAuthService = card3dsResponseAuthService;
        this.chargeEligibleForCaptureService = chargeEligibleForCaptureService;
        this.delayedCaptureService = delayedCaptureService;
        this.chargeCancelService = chargeCancelService;
        this.applePayService = applePayService;
        this.googlePayService = googlePayService;
        this.tokenService = tokenService;
        this.motoApiCardNumberValidationService = motoApiCardNumberValidationService;
        this.chargeService = chargeService;
    }

    @POST
    @Path("/v1/frontend/charges/{chargeId}/wallets/apple")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "Authorise ApplePay payment",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "202", description = "Accepted - payment has been submitted for authorisation and awaiting response from payment service provider"),
                    @ApiResponse(responseCode = "400", description = "Bad request - invalid payload or the payment has been declined",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "402", description = "Gateway error",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "Unprocessable Entity - Invalid payload or missing mandatory attributes",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found"),
                    @ApiResponse(responseCode = "500", description = "Internal server error"),
            }
    )
    public Response authoriseCharge(@Parameter(example = "b02b63b370fd35418ad66b0101", description = "Charge external ID")
                                    @PathParam("chargeId") String chargeId,
                                    @NotNull @Valid ApplePayAuthRequest applePayAuthRequest) {
        logger.info("Received encrypted payload for charge with id {} ", chargeId);
        Charge charge = chargeService.findCharge(chargeId).orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeId));
        return applePayService.authorise(chargeId, applePayAuthRequest, charge.getPaymentGatewayName());
    }

    @POST
    @Path("/v1/frontend/charges/{chargeId}/wallets/google")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "Authorise GooglePay payment",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "202", description = "Accepted - payment has been submitted for authorisation and awaiting response from payment service provider"),
                    @ApiResponse(responseCode = "400", description = "Bad request - invalid payload or the payment has been declined",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "402", description = "Gateway error",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "Unprocessable Entity - Invalid payload or missing mandatory attributes",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found"),
                    @ApiResponse(responseCode = "500", description = "Internal server error"),
            }
    )
    public Response authoriseCharge(@Parameter(example = "b02b63b370fd35418ad66b0101", description = "Charge external ID")
                                    @PathParam("chargeId") String chargeId,
                                    @NotNull @Valid GooglePayAuthRequest googlePayAuthRequest) {
        logger.info("Received encrypted payload for charge with id {} ", chargeId);
        logger.info("Received wallet payment info \n{} \nfor charge with id {}", googlePayAuthRequest.getPaymentInfo().toString(), chargeId);
        Charge charge = chargeService.findCharge(chargeId).orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeId));
        return googlePayService.authorise(chargeId, googlePayAuthRequest, charge.getPaymentGatewayName());
    }

    @POST
    @Path("/v1/frontend/charges/{chargeId}/wallets/stripe/google")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "Authorise GooglePay payment",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "202", description = "Accepted - payment has been submitted for authorisation and awaiting response from payment service provider"),
                    @ApiResponse(responseCode = "400", description = "Bad request - invalid payload or the payment has been declined",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "402", description = "Gateway error",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "Unprocessable Entity - Invalid payload or missing mandatory attributes",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found"),
                    @ApiResponse(responseCode = "500", description = "Internal server error"),
            }
    )
    public Response authoriseStripeGooglePayPayment(@Parameter(example = "b02b63b370fd35418ad66b0101", description = "Charge external ID")
                                    @PathParam("chargeId") String chargeId,
                                    @NotNull @Valid StripeGooglePayAuthRequest googlePayAuthRequest) {
        logger.info("Received wallet payment info \n{} \nfor charge with id {}", googlePayAuthRequest.getPaymentInfo().toString(), chargeId);
        Charge charge = chargeService.findCharge(chargeId).orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeId));
        return googlePayService.authoriseStripeGooglePay(chargeId, googlePayAuthRequest, charge.getPaymentGatewayName());
    }

    @POST
    @Path("/v1/frontend/charges/{chargeId}/cards")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "Authorise charge",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "202", description = "Accepted - payment has been submitted for authorisation and awaiting response from payment service provider"),
                    @ApiResponse(responseCode = "400", description = "Bad request - invalid payload or the payment has been declined",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "402", description = "Gateway error",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "Unprocessable Entity - Invalid payload or missing mandatory attributes",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found - charge not found"),
                    @ApiResponse(responseCode = "500", description = "Internal server error - For gateway errors or anything else not handled"),
            }
    )
    public Response authoriseCharge(@Parameter(example = "b02b63b370fd35418ad66b0101", description = "Charge external ID")
                                    @PathParam("chargeId") String chargeId,
                                    @Valid AuthCardDetails authCardDetails) {
        AuthorisationResponse response = cardAuthoriseService.doAuthoriseWeb(chargeId, authCardDetails);

        return response.getGatewayError().map(this::handleError)
                .orElseGet(() -> response.getAuthoriseStatus().map(status -> handleAuthResponse(chargeId, status))
                        .orElseGet(() -> ResponseUtil.serviceErrorResponse("InterpretedStatus not found for Gateway response")));
    }

    private Response handleAuthResponse(String chargeId, AuthoriseStatus authoriseStatus) {
        // TODO The following if statement can be deleted as it covers a uniquely epdq case. See PP-1494:
        // "A charge at AUTHORISATION SUBMITTED should appear externally to be an error. i.e. payment can not be continued. 
        // This is because ePDQ can defer authorisation for instance during a planned system outage. However we require 
        // authorisation to be immediate so we should not support this. ePDQ have assured us it happens extremely rarely 
        // so it is acceptable to treat this as an error."
        if (authoriseStatus.equals(AuthoriseStatus.SUBMITTED)) {
            logger.info("Charge {}: authorisation was deferred.", chargeId);
            return badRequestResponse("This transaction was deferred.");
        }

        if (isAuthorisationDeclined(authoriseStatus)) {
            return badRequestResponse("This transaction was declined.");
        }

        return ResponseUtil.successResponseWithEntity(ImmutableMap.of("status", authoriseStatus.getMappedChargeStatus().toString()));
    }

    @POST
    @Path("/v1/frontend/charges/{chargeId}/3ds")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "Authorise 3DS charge",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "202", description = "Accepted - payment has been submitted for 3ds authorisation and awaiting response from payment service provider"),
                    @ApiResponse(responseCode = "400", description = "Bad request - invalid payload or the payment has been declined",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "402", description = "Gateway error",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found - charge not found"),
                    @ApiResponse(responseCode = "500", description = "Internal server error - For gateway errors or anything else not handled"),
            }
    )
    public Response authorise3dsCharge(@Parameter(example = "b02b63b370fd35418ad66b0101", description = "Charge external ID")
                                       @PathParam("chargeId") String chargeId, Auth3dsResult auth3DsResult) {
        Gateway3DSAuthorisationResponse response = card3dsResponseAuthService.process3DSecureAuthorisation(chargeId, auth3DsResult);

        if (response.isSuccessful()) {
            return ResponseUtil.successResponseWithEntity(
                    Map.of(
                            "status", response.getMappedChargeStatus().toString()
                    ));
        }
        if (response.isException()) {
            return gatewayErrorResponse("There was an error when attempting to authorise the transaction.");
        }
        return ResponseUtil.badRequestResponseWithEntity(
                Map.of(
                        "status", response.getMappedChargeStatus().toString()
                )
        );
    }

    @POST
    @Path("/v1/frontend/charges/{chargeId}/capture")
    @Operation(
            summary = "Mark charge as eligible for capture",
            description = "Marks charge as eligible (or ready - for delayed capture) for capture and also adds charge to capture queue (if not delayed capture).",
            responses = {
                    @ApiResponse(responseCode = "204", description = "No content"),
                    @ApiResponse(responseCode = "400", description = "Bad request - if charge is not in correct state"),
                    @ApiResponse(responseCode = "404", description = "Not found - charge not found"),
                    @ApiResponse(responseCode = "500", description = "Internal server error")
            }
    )
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response captureCharge(@Parameter(example = "b02b63b370fd35418ad66b0101", description = "Charge external ID")
                                  @PathParam("chargeId") String chargeId) {
        chargeEligibleForCaptureService.markChargeAsEligibleForCapture(chargeId);
        return ResponseUtil.noContentResponse();
    }

    @POST
    @Path("/v1/api/accounts/{accountId}/charges/{chargeId}/capture")
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "Mark delayed capture charge as eligible for capture and adds charge to capture queue",
            description = "This endpoint should be called to capture a delayed capture charge. The charge needs to have been previously marked as AWAITING CAPTURE REQUEST for this call to succeed. " +
                    "When a charge is in any of the states CAPTURED, CAPTURE APPROVED, CAPTURE APPROVED RETRY, CAPTURE READY, CAPTURE SUBMITTED then nothing happens and the response will be a 204. " +
                    "When a charge is in a status that cannot transition (eg. none of the above) then 409 response is returned. ",
            responses = {
                    @ApiResponse(responseCode = "204", description = "No content"),
                    @ApiResponse(responseCode = "409", description = "Conflict - if charge is not in correct state"),
                    @ApiResponse(responseCode = "404", description = "Not found - charge not found"),
                    @ApiResponse(responseCode = "500", description = "Internal server error")
            }
    )
    public Response markChargeAsCaptureApproved(@PathParam("accountId") Long accountId,
                                                @PathParam("chargeId") String chargeId,
                                                @Context UriInfo uriInfo) {
        logger.info("Mark charge as CAPTURE APPROVED [charge_external_id={}]", chargeId);
        delayedCaptureService.markDelayedCaptureChargeAsCaptureApproved(chargeId);
        return ResponseUtil.noContentResponse();
    }

    @POST
    @Path("/v1/api/accounts/{accountId}/charges/{chargeId}/cancel")
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "Cancel charge",
            responses = {
                    @ApiResponse(responseCode = "202", description = "Accepted - operation already in progress"),
                    @ApiResponse(responseCode = "204", description = "No content"),
                    @ApiResponse(responseCode = "400", description = "Bad request - charge is not in correct state",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found - charge not found")
            }
    )
    public Response cancelCharge(@Parameter(example = "1", description = "Gateway account ID")
                                 @PathParam("accountId") Long accountId,
                                 @Parameter(example = "b02b63b370fd35418ad66b0101", description = "Charge external ID")
                                 @PathParam("chargeId") String chargeId) {
        return chargeCancelService.doSystemCancel(chargeId, accountId)
                .map(chargeEntity -> Response.noContent().build())
                .orElseGet(() -> ResponseUtil.responseWithChargeNotFound(chargeId));
    }

    @POST
    @Path("/v1/api/charges/authorise")
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "Authorise MOTO (api) payment",
            responses = {
                    @ApiResponse(responseCode = "204", description = "No content"),
                    @ApiResponse(responseCode = "400", description = "Bad request - invalid one time token or one_time_token has already been used",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "402", description = "Authorisation declined",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "Unprocessable Entity - Invalid payload or missing mandatory attributes",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found - charge not found"),
                    @ApiResponse(responseCode = "500", description = "Authorisation error"),
            }
    )
    public Response authorise(@Valid @NotNull AuthoriseRequest authoriseRequest) {
        // Fields are removed from the MDC when the API responds in LoggingMDCResponseFilter 
        MDC.put(SECURE_TOKEN, authoriseRequest.getOneTimeToken());
        authoriseRequest.validate();

        TokenEntity tokenEntity = tokenService.validateAndMarkTokenAsUsedForMotoApi(authoriseRequest.getOneTimeToken());
        MDCUtils.addChargeAndGatewayAccountDetailsToMDC(tokenEntity.getChargeEntity());
        CardInformation cardInformation = motoApiCardNumberValidationService.validateCardNumber(tokenEntity.getChargeEntity(), authoriseRequest.getCardNumber());

        AuthorisationResponse response = cardAuthoriseService.doAuthoriseMotoApi(tokenEntity.getChargeEntity(), cardInformation, authoriseRequest);
        return handleAuthResponseForMotoApi(response);
    }

    @POST
    @Path("/v1/frontend/charges/{chargeId}/cancel")
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "Cancel charge (action by user)",
            responses = {
                    @ApiResponse(responseCode = "202", description = "Accepted - operation already in progress"),
                    @ApiResponse(responseCode = "204", description = "No content"),
                    @ApiResponse(responseCode = "400", description = "Bad request - charge is not in correct state",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found - charge not found")
            }
    )
    public Response userCancelCharge(@Parameter(example = "b02b63b370fd35418ad66b0101", description = "Charge external ID")
                                     @PathParam("chargeId") String chargeId) {
        return chargeCancelService.doUserCancel(chargeId)
                .map(chargeEntity -> Response.noContent().build())
                .orElseGet(() -> ResponseUtil.responseWithChargeNotFound(chargeId));
    }

    private Response handleError(GatewayError error) {
        switch (error.getErrorType()) {
            case GATEWAY_CONNECTION_TIMEOUT_ERROR:
            case GATEWAY_ERROR:
                return gatewayErrorResponse(error.getMessage());
            default:
                return badRequestResponse(error.getMessage());
        }
    }

    private Response handleAuthResponseForMotoApi(AuthorisationResponse response) {
        if (response.getGatewayError().isPresent()) {
            throw new AuthorisationErrorException();
        } else if (response.getAuthoriseStatus().isPresent()) {
            switch (response.getAuthoriseStatus().get().getMappedChargeStatus()) {
                case AUTHORISATION_SUCCESS:
                    return Response.noContent().build();
                case AUTHORISATION_REJECTED:
                case AUTHORISATION_CANCELLED:
                    throw new AuthorisationRejectedException();
                case AUTHORISATION_ERROR:
                case AUTHORISATION_SUBMITTED:
                    throw new AuthorisationErrorException();
                default:
                    return ResponseUtil.serviceErrorResponse("Authorisation status unexpected");
            }
        } else {
            return ResponseUtil.serviceErrorResponse("InterpretedStatus not found for Gateway response");
        }
    }

    private static boolean isAuthorisationDeclined(AuthoriseStatus authoriseStatus) {
        return authoriseStatus.equals(AuthoriseStatus.REJECTED) ||
                authoriseStatus.equals(AuthoriseStatus.ERROR);
    }
}
