package uk.gov.pay.connector.resources;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.model.GatewayError;
import uk.gov.pay.connector.model.domain.Card;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.service.*;
import uk.gov.pay.connector.util.ResponseUtil;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.util.Optional;

import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.resources.ApiPaths.*;
import static uk.gov.pay.connector.resources.CardDetailsValidator.isWellFormattedCardDetails;
import static uk.gov.pay.connector.util.ResponseUtil.badRequestResponse;
import static uk.gov.pay.connector.util.ResponseUtil.serviceErrorResponse;

@Path("/")
public class CardResource {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final CardAuthoriseService cardAuthoriseService;
    private final CardCaptureService cardCaptureService;
    private final ChargeCancelService chargeCancelService;

    @Inject
    public CardResource(CardAuthoriseService cardAuthoriseService, CardCaptureService cardCaptureService, ChargeCancelService chargeCancelService) {
        this.cardAuthoriseService = cardAuthoriseService;
        this.cardCaptureService = cardCaptureService;
        this.chargeCancelService = chargeCancelService;
    }

    @POST
    @Path(FRONTEND_CHARGE_AUTHORIZE_API_PATH)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response authoriseCharge(@PathParam("chargeId") String chargeId, Card cardDetails) {

        if (!isWellFormattedCardDetails(cardDetails)) {
            return badRequestResponse("Values do not match expected format/length.");
        }
        GatewayResponse<BaseAuthoriseResponse> response = cardAuthoriseService.doAuthorise(chargeId, cardDetails);

        Optional<BaseAuthoriseResponse> baseResponse = response.getBaseResponse();
        if (baseResponse.isPresent() && !baseResponse.get().isAuthorised()) {
            return badRequestResponse("This transaction was declined.");
        }

        return handleGatewayResponse(response);
    }

    @POST
    @Path(FRONTEND_CHARGE_CAPTURE_API_PATH)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response captureCharge(@PathParam("chargeId") String chargeId) {
        return handleGatewayResponse(cardCaptureService.doCapture(chargeId));
    }

    @POST
    @Path(CHARGE_CANCEL_API_PATH)
    @Produces(APPLICATION_JSON)
    public Response cancelCharge(@PathParam("accountId") Long accountId, @PathParam("chargeId") String chargeId) {
        return handleGatewayResponse(chargeCancelService.doSystemCancel(chargeId, accountId), chargeId);
    }

    @POST
    @Path(FRONTEND_CHARGE_CANCEL_API_PATH)
    @Produces(APPLICATION_JSON)
    public Response userCancelCharge(@PathParam("chargeId") String chargeId) {
        return handleGatewayResponse(chargeCancelService.doUserCancel(chargeId), chargeId);
    }

    private Response handleError(GatewayError error) {
        switch (error.getErrorType()) {
            case UNEXPECTED_STATUS_CODE_FROM_GATEWAY:
            case MALFORMED_RESPONSE_RECEIVED_FROM_GATEWAY:
            case GATEWAY_URL_DNS_ERROR:
            case GATEWAY_CONNECTION_TIMEOUT_ERROR:
            case GATEWAY_CONNECTION_SOCKET_ERROR:
                return serviceErrorResponse(error.getMessage());
        }
        /* FIXME: This state doesn't sound right here. particularly when cancelling and
         gateway sends a valid but cancellation could not be done, why are we responding caller as BAD REQUEST? (Request was fine)
         Either way if we got this far it cannot be a BAD REQUEST !!!
         */
        return badRequestResponse(error.getMessage());
    }

    private Response handleGatewayResponse(GatewayResponse<? extends BaseResponse> response) {
        return response.getGatewayError()
                .map(this::handleError)
                .orElseGet(ResponseUtil::noContentResponse);
    }

    private Response handleGatewayResponse(Optional<GatewayResponse<BaseCancelResponse>> responseMaybe, String chargeId) {
        return responseMaybe
                .map(this::handleGatewayResponse)
                .orElseGet(() -> {
                    logger.error("Error during cancellation of charge {} - CancelService did not return a GatewayResponse", chargeId);
                    return serviceErrorResponse(format("something went wrong during cancellation of charge %s", chargeId));
                });
    }
}
