package uk.gov.pay.connector.resources;

import fj.F;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import static fj.data.Either.reduce;
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
    private final Logger logger = LoggerFactory.getLogger(CardResource.class);

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
            return badRequestResponse(logger, "Values do not match expected format/length.");
        }

        return reduce(
                cardAuthoriseService
                        .doAuthorise(chargeId, cardDetails)
                        .bimap(handleError, handleGatewayResponse));
    }

    @POST
    @Path(FRONTEND_CAPTURE_RESOURCE)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response captureCharge(@PathParam("chargeId") String chargeId) {

        return reduce(
                cardCaptureService
                        .doCapture(chargeId)
                        .bimap(handleError, handleGatewayResponse)
        );
    }

    @POST
    @Path(CANCEL_CHARGE_RESOURCE)
    @Produces(APPLICATION_JSON)
    public Response cancelCharge(@PathParam("accountId") Long accountId, @PathParam("chargeId") String chargeId) {
        return reduce(
                cardCancelService
                        .doCancel(chargeId, accountId)
                        .bimap(handleError, handleGatewayResponse)
        );
    }

    @POST
    @Path(FRONTEND_CANCEL_RESOURCE)
    @Produces(APPLICATION_JSON)
    public Response userCancelCharge(@PathParam("chargeId") String chargeId) {
        return reduce(
                userCardCancelService
                        .doCancel(chargeId)
                        .bimap(handleError, handleGatewayResponse)
        );
    }

    private F<ErrorResponse, Response> handleError =
            (error) -> {
                switch (error.getErrorType()) {
                    case CHARGE_NOT_FOUND:
                        return notFoundResponse(logger, error.getMessage());
                    case UNEXPECTED_STATUS_CODE_FROM_GATEWAY:
                    case MALFORMED_RESPONSE_RECEIVED_FROM_GATEWAY:
                    case GATEWAY_URL_DNS_ERROR:
                    case GATEWAY_CONNECTION_TIMEOUT_ERROR:
                    case GATEWAY_CONNECTION_SOCKET_ERROR:
                        return serviceErrorResponse(logger, error.getMessage());
                    case OPERATION_ALREADY_IN_PROGRESS:
                        return acceptedResponse(logger, error.getMessage());
                }

                return badRequestResponse(logger, error.getMessage());
            };

    private F<GatewayResponse, Response> handleGatewayResponse =
            response -> response.isSuccessful() ? noContentResponse() :
                    response.isInProgress() ? acceptedResponse(logger, "Request in progress") :
                            handleError.f(response.getError());

}
