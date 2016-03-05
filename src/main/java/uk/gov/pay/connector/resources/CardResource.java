package uk.gov.pay.connector.resources;

import fj.F;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.PayDBIException;
import uk.gov.pay.connector.model.GatewayError;
import uk.gov.pay.connector.model.GatewayResponse;
import uk.gov.pay.connector.model.domain.Card;
import uk.gov.pay.connector.service.CardService;

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
    private final CardService cardService;
    private final Logger logger = LoggerFactory.getLogger(CardResource.class);

    @Inject
    public CardResource(CardService cardService) {
        this.cardService = cardService;
    }

    @POST
    @Path(FRONTEND_AUTHORIZATION_RESOURCE)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response authoriseCharge(@PathParam("chargeId") Long chargeId, Card cardDetails) {
        if (!isWellFormattedCardDetails(cardDetails)) {
            return badRequestResponse(logger, "Values do not match expected format/length.");
        }

        return reduce(
                cardService
                        .doAuthorise(chargeId, cardDetails)
                        .bimap(handleError, handleGatewayResponse));
    }

    @POST
    @Path(FRONTEND_CAPTURE_RESOURCE)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response captureCharge(@PathParam("chargeId") Long chargeId) throws PayDBIException {
        return reduce(
                cardService
                        .doCapture(chargeId)
                        .bimap(handleError, handleGatewayResponse)
        );
    }

    @POST
    @Path(CANCEL_CHARGE_PATH)
    @Produces(APPLICATION_JSON)
    public Response cancelCharge(@PathParam("accountId") Long accountId, @PathParam("chargeId") Long chargeId) {
        return reduce(
                cardService
                        .doCancel(chargeId, accountId)
                        .bimap(handleError, handleGatewayResponse)
        );
    }

    private F<GatewayError, Response> handleError =
            (error) -> {
                switch (error.getErrorType()) {
                    case ChargeNotFound:
                        return notFoundResponse(logger, error.getMessage());
                    case UnexpectedStatusCodeFromGateway:
                    case MalformedResponseReceivedFromGateway:
                    case GatewayUrlDnsError:
                    case GatewayConnectionTimeoutError:
                    case GatewayConnectionSocketError:
                    case IllegalStateError:
                        return serviceErrorResponse(logger, error.getMessage());
                    case OperationAlreadyInProgress:
                    return acceptedResponse(logger, error.getMessage());
                }

                return badRequestResponse(logger, error.getMessage());
            };

    private F<GatewayResponse, Response> handleGatewayResponse =
            response -> response.isSuccessful() ?
                    noContentResponse() :
                    handleError.f(response.getError());

}
