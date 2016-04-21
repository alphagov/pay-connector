package uk.gov.pay.connector.resources;

import uk.gov.pay.connector.model.ErrorResponse;
import uk.gov.pay.connector.model.GatewayResponse;
import uk.gov.pay.connector.model.domain.Card;
import uk.gov.pay.connector.service.CardAuthoriseService;
import uk.gov.pay.connector.service.CardCancelService;
import uk.gov.pay.connector.service.CardCaptureService;
import uk.gov.pay.connector.service.UserCardCancelService;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.resources.ApiPaths.*;
import static uk.gov.pay.connector.resources.CardDetailsValidator.isWellFormattedCardDetails;
import static uk.gov.pay.connector.util.ResponseUtil.*;

@Path("/")
public class CardResource {
    private final CardAuthoriseService cardAuthoriseService;
    private final CardCaptureService cardCaptureService;
    private final CardCancelService cardCancelService;
    private final UserCardCancelService userCardCancelService;

    @Inject
    public CardResource(CardAuthoriseService cardAuthoriseService, CardCaptureService cardCaptureService, CardCancelService cardCancelService, UserCardCancelService userCardCancelService) {
        this.cardAuthoriseService = cardAuthoriseService;
        this.cardCaptureService = cardCaptureService;
        this.cardCancelService = cardCancelService;
        this.userCardCancelService = userCardCancelService;
    }

    @POST
    @Path(FRONTEND_AUTHORIZATION_RESOURCE)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response authoriseCharge(@PathParam("chargeId") String chargeId, Card cardDetails) {

        if (!isWellFormattedCardDetails(cardDetails)) {
            return badRequestResponse("Values do not match expected format/length.");
        }
        return handleGatewayResponse(cardAuthoriseService.doAuthorise(chargeId, cardDetails));
    }

    @POST
    @Path(FRONTEND_CAPTURE_RESOURCE)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response captureCharge(@PathParam("chargeId") String chargeId) {
        return handleGatewayResponse(cardCaptureService.doCapture(chargeId));
    }

    @POST
    @Path(CANCEL_CHARGE_RESOURCE)
    @Produces(APPLICATION_JSON)
    public Response cancelCharge(@PathParam("accountId") Long accountId, @PathParam("chargeId") String chargeId) {
        return handleGatewayResponse(cardCancelService.doCancel(chargeId, accountId));
    }

    @POST
    @Path(FRONTEND_CANCEL_RESOURCE)
    @Produces(APPLICATION_JSON)
    public Response userCancelCharge(@PathParam("chargeId") String chargeId) {
        return handleGatewayResponse(userCardCancelService.doCancel(chargeId));
    }

    private Response handleError(ErrorResponse error) {
        switch (error.getErrorType()) {
            case UNEXPECTED_STATUS_CODE_FROM_GATEWAY:
            case MALFORMED_RESPONSE_RECEIVED_FROM_GATEWAY:
            case GATEWAY_URL_DNS_ERROR:
            case GATEWAY_CONNECTION_TIMEOUT_ERROR:
            case GATEWAY_CONNECTION_SOCKET_ERROR:
                return serviceErrorResponse(error.getMessage());
        }
        return badRequestResponse(error.getMessage());
    }

    private Response handleGatewayResponse(GatewayResponse response) {
        return response.isSuccessful() ? noContentResponse() :
                    response.isInProgress() ? acceptedResponse("Request in progress") :
                            handleError(response.getError());
    }

}
