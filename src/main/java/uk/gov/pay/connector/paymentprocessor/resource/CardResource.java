package uk.gov.pay.connector.paymentprocessor.resource;

import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.service.ChargeCancelService;
import uk.gov.pay.connector.charge.service.ChargeEligibleForCaptureService;
import uk.gov.pay.connector.charge.service.DelayedCaptureService;
import uk.gov.pay.connector.charge.service.motoapi.MotoApiCardNumberValidationService;
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
import uk.gov.pay.connector.util.ResponseUtil;
import uk.gov.pay.connector.wallets.applepay.ApplePayService;
import uk.gov.pay.connector.wallets.applepay.api.ApplePayAuthRequest;
import uk.gov.pay.connector.wallets.googlepay.GooglePayService;
import uk.gov.pay.connector.wallets.googlepay.api.GooglePayAuthRequest;

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
import static uk.gov.pay.connector.util.ResponseUtil.serviceErrorResponse;

@Path("/")
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

    @Inject
    public CardResource(CardAuthoriseService cardAuthoriseService, Card3dsResponseAuthService card3dsResponseAuthService,
                        ChargeEligibleForCaptureService chargeEligibleForCaptureService, DelayedCaptureService delayedCaptureService,
                        ChargeCancelService chargeCancelService, ApplePayService applePayService, GooglePayService googlePayService,
                        TokenService tokenService, MotoApiCardNumberValidationService motoApiCardNumberValidationService) {
        this.cardAuthoriseService = cardAuthoriseService;
        this.card3dsResponseAuthService = card3dsResponseAuthService;
        this.chargeEligibleForCaptureService = chargeEligibleForCaptureService;
        this.delayedCaptureService = delayedCaptureService;
        this.chargeCancelService = chargeCancelService;
        this.applePayService = applePayService;
        this.googlePayService = googlePayService;
        this.tokenService = tokenService;
        this.motoApiCardNumberValidationService = motoApiCardNumberValidationService;
    }

    @POST
    @Path("/v1/frontend/charges/{chargeId}/wallets/apple")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response authoriseCharge(@PathParam("chargeId") String chargeId, 
                                    @NotNull @Valid ApplePayAuthRequest applePayAuthRequest) {
        logger.info("Received encrypted payload for charge with id {} ", chargeId);
        return applePayService.authorise(chargeId, applePayAuthRequest);
    }

    @POST
    @Path("/v1/frontend/charges/{chargeId}/wallets/google")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response authoriseCharge(@PathParam("chargeId") String chargeId,
                                    @NotNull @Valid GooglePayAuthRequest googlePayAuthRequest) {
        logger.info("Received encrypted payload for charge with id {} ", chargeId);
        logger.info("Received wallet payment info \n{} \nfor charge with id {}", googlePayAuthRequest.getPaymentInfo().toString(), chargeId);
        return googlePayService.authorise(chargeId, googlePayAuthRequest);
    }

    @POST
    @Path("/v1/frontend/charges/{chargeId}/cards")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response authoriseCharge(@PathParam("chargeId") String chargeId,
                                    @Valid AuthCardDetails authCardDetails) {
        AuthorisationResponse response = cardAuthoriseService.doAuthorise(chargeId, authCardDetails);

        return response.getGatewayError().map(error -> handleError(chargeId, error))
                .orElseGet(() -> response.getAuthoriseStatus().map(status -> handleAuthResponse(chargeId, status))
                        .orElseGet(() -> ResponseUtil.serviceErrorResponse("InterpretedStatus not found for Gateway response")));
    }

    private Response handleAuthResponse(String chargeId, AuthoriseStatus authoriseStatus) {
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
    public Response authorise3dsCharge(@PathParam("chargeId") String chargeId, Auth3dsResult auth3DsResult) {
        Gateway3DSAuthorisationResponse response = card3dsResponseAuthService.process3DSecureAuthorisation(chargeId, auth3DsResult);

        if (response.isSuccessful()) {
            return ResponseUtil.successResponseWithEntity(
                    Map.of(
                            "status", response.getMappedChargeStatus().toString()
                    ));
        }
        if (response.isException()) {
            return serviceErrorResponse("There was an error when attempting to authorise the transaction.");
        }
        return ResponseUtil.badRequestResponseWithEntity(
                Map.of(
                        "status", response.getMappedChargeStatus().toString()
                )
        );
    }

    @POST
    @Path("/v1/frontend/charges/{chargeId}/capture")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response captureCharge(@PathParam("chargeId") String chargeId) {
        chargeEligibleForCaptureService.markChargeAsEligibleForCapture(chargeId);
        return ResponseUtil.noContentResponse();
    }

    @POST
    @Path("/v1/api/accounts/{accountId}/charges/{chargeId}/capture")
    @Produces(APPLICATION_JSON)
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
    public Response cancelCharge(@PathParam("accountId") Long accountId, @PathParam("chargeId") String chargeId) {
        return chargeCancelService.doSystemCancel(chargeId, accountId)
                .map(chargeEntity -> Response.noContent().build())
                .orElseGet(() -> ResponseUtil.responseWithChargeNotFound(chargeId));
    }

    @POST
    @Path("/v1/api/charges/authorise")
    @Produces(APPLICATION_JSON)
    public Response authorise(@Valid @NotNull AuthoriseRequest authoriseRequest) {
        authoriseRequest.validate();
        TokenEntity tokenEntity = tokenService.validateAndMarkTokenAsUsedForMotoApi(authoriseRequest.getOneTimeToken());

        motoApiCardNumberValidationService.validateCardNumber(tokenEntity.getChargeEntity(), authoriseRequest.getCardNumber());

        return Response.noContent().build();
    }

    @POST
    @Path("/v1/frontend/charges/{chargeId}/cancel")
    @Produces(APPLICATION_JSON)
    public Response userCancelCharge(@PathParam("chargeId") String chargeId) {
        return chargeCancelService.doUserCancel(chargeId)
                .map(chargeEntity -> Response.noContent().build())
                .orElseGet(() -> ResponseUtil.responseWithChargeNotFound(chargeId));
    }


    private Response handleError(String chargeId, GatewayError error) {
        switch (error.getErrorType()) {
            case GATEWAY_CONNECTION_TIMEOUT_ERROR:
            case GATEWAY_ERROR:
                return serviceErrorResponse(error.getMessage());
            default:
                return badRequestResponse(error.getMessage());
        }
    }

    private static boolean isAuthorisationDeclined(AuthoriseStatus authoriseStatus) {
        return authoriseStatus.equals(AuthoriseStatus.REJECTED) ||
                authoriseStatus.equals(AuthoriseStatus.ERROR);
    }
}
