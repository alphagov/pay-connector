package uk.gov.pay.connector.resources;

import fj.F;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.PayDBIException;
import uk.gov.pay.connector.model.GatewayError;
import uk.gov.pay.connector.model.GatewayResponse;
import uk.gov.pay.connector.model.domain.Card;
import uk.gov.pay.connector.service.CardService;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;

import static fj.data.Either.reduce;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.resources.CardDetailsValidator.isWellFormattedCardDetails;
import static uk.gov.pay.connector.util.ResponseUtil.*;

@Path("/")
public class CardResource {

    public static final String AUTHORIZATION_FRONTEND_RESOURCE_PATH = "/v1/frontend/charges/{chargeId}/cards";
    public static final String CAPTURE_FRONTEND_RESOURCE_PATH = "/v1/frontend/charges/{chargeId}/capture";
    public static final String CANCEL_CHARGE_PATH = "/v1/api/charges/{chargeId}/cancel";
    private final CardService cardService;
    private final Logger logger = LoggerFactory.getLogger(CardResource.class);

    public CardResource(CardService cardService) {
        this.cardService = cardService;
    }

    @POST
    @Path(AUTHORIZATION_FRONTEND_RESOURCE_PATH)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response authoriseCharge(@PathParam("chargeId") String chargeId, Card cardDetails) {
        if (!isWellFormattedCardDetails(cardDetails)) {
            return badRequestResponse(logger, "Values do not match expected format/length.");
        }

        return reduce(
                cardService
                        .doAuthorise(chargeId, cardDetails)
                        .bimap(handleError, handleGatewayResponse));
    }

    @POST
    @Path(CAPTURE_FRONTEND_RESOURCE_PATH)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response captureCharge(@PathParam("chargeId") String chargeId) throws PayDBIException {
        return reduce(
                cardService
                        .doCapture(chargeId)
                        .bimap(handleError, handleGatewayResponse)
        );
    }

    @POST
    @Path(CANCEL_CHARGE_PATH)
    @Produces(APPLICATION_JSON)
    public Response cancelCharge(@PathParam("chargeId") String chargeId) {
        return reduce(
                cardService
                        .doCancel(chargeId)
                        .bimap(handleError, handleGatewayResponse)
        );
    }

    private F<GatewayError, Response> handleError =
            (error) -> {
                switch (error.getErrorType()) {
                    case ChargeNotFound:
                        return notFoundResponse(logger, error.getMessage());
                    case UnexpectedStatusCodeFromGateway:
                        return serviceErrorResponse(logger, "Unexpected Response Code From Gateway");
                    case MalformedResponseReceivedFromGateway:
                    case UnknownHostException:
                        return serviceErrorResponse(logger, error.getMessage());
                }
                return badRequestResponse(logger, error.getMessage());
            };

    private F<GatewayResponse, Response> handleGatewayResponse =
            response -> response.isSuccessful() ?
                    notContentResponse() :
                    handleError.f(response.getError());

}
