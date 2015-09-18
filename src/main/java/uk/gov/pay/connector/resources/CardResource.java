package uk.gov.pay.connector.resources;

import fj.F;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.PayDBIException;
import uk.gov.pay.connector.model.GatewayError;
import uk.gov.pay.connector.model.GatewayResponse;
import uk.gov.pay.connector.model.domain.Card;
import uk.gov.pay.connector.service.CardProcessor;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import static fj.data.Either.reduce;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.model.GatewayErrorType.ChargeNotFound;
import static uk.gov.pay.connector.resources.CardDetailsValidator.isWellFormattedCardDetails;
import static uk.gov.pay.connector.util.ResponseUtil.badRequestResponse;
import static uk.gov.pay.connector.util.ResponseUtil.notFoundResponse;

@Path("/")
public class CardResource {

    private final CardProcessor cardProcessor;
    private final Logger logger = LoggerFactory.getLogger(CardResource.class);

    public CardResource(CardProcessor cardProcessor) {
        this.cardProcessor = cardProcessor;
    }

    @POST
    @Path("/v1/frontend/charges/{chargeId}/cards")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response authoriseCharge(@PathParam("chargeId") String chargeId, Card cardDetails) {

        if (!isWellFormattedCardDetails(cardDetails)) {
            return badRequestResponse(logger, "Values do not match expected format/length.");
        }

        return reduce(cardProcessor.doAuthorise(chargeId, cardDetails)
                .bimap(handleWorldpayResponse, handleError));
    }

    @POST
    @Path("/v1/frontend/charges/{chargeId}/capture")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response captureCharge(@PathParam("chargeId") String chargeId) throws PayDBIException {

        return reduce(cardProcessor.doCapture(chargeId)
                .bimap(handleWorldpayResponse, handleError));
    }


    private F<GatewayError, Response> handleError =
            error -> ChargeNotFound.equals(error.getErrorType()) ? notFoundResponse(logger, error.getMessage()) : badRequestResponse(logger, error.getMessage());

    private F<GatewayResponse, Response> handleWorldpayResponse =
            response -> response.isSuccessful() ?
                    Response.noContent().build() :
                    handleError.f(response.getError());

}
