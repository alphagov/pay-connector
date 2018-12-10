package uk.gov.pay.connector.paymentprocessor.resource;

import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.service.ChargeCancelService;
import uk.gov.pay.connector.gateway.model.Auth3dsDetails;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse.AuthoriseStatus;
import uk.gov.pay.connector.gateway.model.response.Gateway3DSAuthorisationResponse;
import uk.gov.pay.connector.paymentprocessor.api.AuthorisationResponse;
import uk.gov.pay.connector.paymentprocessor.service.Card3dsResponseAuthService;
import uk.gov.pay.connector.paymentprocessor.service.CardAuthoriseService;
import uk.gov.pay.connector.paymentprocessor.service.CardCaptureService;
import uk.gov.pay.connector.util.ResponseUtil;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.common.validator.AuthCardDetailsValidator.isWellFormatted;
import static uk.gov.pay.connector.util.ResponseUtil.badRequestResponse;
import static uk.gov.pay.connector.util.ResponseUtil.serviceErrorResponse;

@Path("/")
public class CardResource {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final CardAuthoriseService cardAuthoriseService;
    private final Card3dsResponseAuthService card3dsResponseAuthService;
    private final CardCaptureService cardCaptureService;
    private final ChargeCancelService chargeCancelService;

    @Inject
    public CardResource(CardAuthoriseService cardAuthoriseService, Card3dsResponseAuthService card3dsResponseAuthService,
                        CardCaptureService cardCaptureService, ChargeCancelService chargeCancelService) {
        this.cardAuthoriseService = cardAuthoriseService;
        this.card3dsResponseAuthService = card3dsResponseAuthService;
        this.cardCaptureService = cardCaptureService;
        this.chargeCancelService = chargeCancelService;
    }

    @POST
    @Path("/v1/frontend/charges/{chargeId}/cards")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response authoriseCharge(@PathParam("chargeId") String chargeId, AuthCardDetails authCardDetails) {

        if (!isWellFormatted(authCardDetails)) {
            return badRequestResponse("Values do not match expected format/length.");
        }
        AuthorisationResponse response = cardAuthoriseService.doAuthorise(chargeId, authCardDetails);

        return response.getGatewayError().map(this::handleError)
                .orElseGet(() -> response.getAuthoriseStatus().map(this::handleAuthResponse)
                        .orElseGet(() -> ResponseUtil.serviceErrorResponse("InterpretedStatus not found for Gateway response")));
    }

    private Response handleAuthResponse(AuthoriseStatus authoriseStatus) {
        if (authoriseStatus.equals(AuthoriseStatus.SUBMITTED)) {
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
    public Response authorise3dsCharge(@PathParam("chargeId") String chargeId, Auth3dsDetails auth3DsDetails) {
        Gateway3DSAuthorisationResponse response = card3dsResponseAuthService.process3DSecureAuthorisation(chargeId, auth3DsDetails);
        return response.isDeclined() ? badRequestResponse("This transaction was declined.") : handleGateway3DSAuthoriseResponse(response);
    }

    private Response handleGateway3DSAuthoriseResponse(Gateway3DSAuthorisationResponse response) {
        if (response.isSuccessful()) {
            return ResponseUtil.successResponseWithEntity(
                    ImmutableMap.of(
                            "status", response.getMappedChargeStatus().toString()
                    ));
        } else {
            return serviceErrorResponse("Failed");
        }
    }

    @POST
    @Path("/v1/frontend/charges/{chargeId}/capture")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response captureCharge(@PathParam("chargeId") String chargeId) {
        logger.info("Capture of charge asynchronously [charge_external_id={}]", chargeId);
        cardCaptureService.markChargeAsEligibleForCapture(chargeId);
        return ResponseUtil.noContentResponse();
    }

    @POST
    @Path("/v1/api/accounts/{accountId}/charges/{chargeId}/capture")
    @Produces(APPLICATION_JSON)
    public Response markChargeAsCaptureApproved(@PathParam("accountId") Long accountId,
                                                @PathParam("chargeId") String chargeId,
                                                @Context UriInfo uriInfo) {
        logger.info("Mark charge as CAPTURE APPROVED [charge_external_id={}]", chargeId);
        cardCaptureService.markChargeAsCaptureApproved(chargeId);
        return ResponseUtil.noContentResponse();
    }

    @POST
    @Path("/v1/api/accounts/{accountId}/charges/{chargeId}/cancel")
    @Produces(APPLICATION_JSON)
    public Response cancelCharge(@PathParam("accountId") Long accountId, @PathParam("chargeId") String chargeId) {
        return chargeCancelService.doSystemCancel(chargeId, accountId)
                .map(chargeEntity -> Response.noContent().build())
                .orElse(ResponseUtil.responseWithChargeNotFound(chargeId));
    }

    @POST
    @Path("/v1/frontend/charges/{chargeId}/cancel")
    @Produces(APPLICATION_JSON)
    public Response userCancelCharge(@PathParam("chargeId") String chargeId) {
        return chargeCancelService.doUserCancel(chargeId)
                .map(chargeEntity -> Response.noContent().build())
                .orElse(ResponseUtil.responseWithChargeNotFound(chargeId));
    }

    private Response handleError(GatewayError error) {
        switch (error.getErrorType()) {
            case UNEXPECTED_HTTP_STATUS_CODE_FROM_GATEWAY:
            case MALFORMED_RESPONSE_RECEIVED_FROM_GATEWAY:
            case GATEWAY_URL_DNS_ERROR:
            case GATEWAY_CONNECTION_TIMEOUT_ERROR:
            case GATEWAY_CONNECTION_SOCKET_ERROR:
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
