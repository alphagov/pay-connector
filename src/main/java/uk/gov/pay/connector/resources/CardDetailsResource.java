package uk.gov.pay.connector.resources;

import com.google.common.base.Optional;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.PayDBIException;
import uk.gov.pay.connector.model.CardError;
import uk.gov.pay.connector.model.ChargeStatus;
import uk.gov.pay.connector.util.ResponseUtil;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import java.util.Map;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.model.ChargeStatus.AUTHORIZATION_SUCCESS;
import static uk.gov.pay.connector.model.SandboxCardNumbers.ERROR_CARDS;
import static uk.gov.pay.connector.model.SandboxCardNumbers.GOOD_CARDS;
import static uk.gov.pay.connector.resources.CardDetailsValidator.CARD_NUMBER_FIELD;
import static uk.gov.pay.connector.resources.CardDetailsValidator.isWellFormattedCardDetails;

@Path("/")
public class CardDetailsResource {

    private ChargeDao chargeDao;

    public CardDetailsResource(ChargeDao chargeDao) {
        this.chargeDao = chargeDao;
    }

    @POST
    @Path("/v1/frontend/charges/{chargeId}/cards")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response addCardDetailsForCharge(@PathParam("chargeId") long chargeId, Map<String, Object> cardDetails) throws PayDBIException {

        if (!isWellFormattedCardDetails(cardDetails)) {
            return responseWithError("Values do not match expected format/length.");
        }

        Optional<Map<String, Object>> maybeCharge = Optional.fromNullable(chargeDao.findById(chargeId));

        if (!maybeCharge.isPresent()) {
            return responseWithChargeNotFound(chargeId);
        } else if (!hasStatusCreated(maybeCharge.get())) {
            return responseWithCardAlreadyProcessed(chargeId);
        }

        String cardNumber = (String) cardDetails.get(CARD_NUMBER_FIELD);

        return respondForCorrespondingSanboxCard(chargeId, cardNumber);
    }

    private Response respondForCorrespondingSanboxCard(long chargeId, String cardNumber) throws PayDBIException {

        if (ERROR_CARDS.containsKey(cardNumber)) {
            CardError errorInfo = ERROR_CARDS.get(cardNumber);
            chargeDao.updateStatus(chargeId, errorInfo.getNewErrorStatus());
            return responseWithError(errorInfo.getErrorMessage());
        }

        if (GOOD_CARDS.contains(cardNumber)) {
            chargeDao.updateStatus(chargeId, AUTHORIZATION_SUCCESS);
            return Response.noContent().build();
        }

        return responseWithError("Unsupported card details.");
    }

    private boolean hasStatusCreated(Map<String, Object> charge) {
        return ChargeStatus.CREATED.getValue().equals(charge.get("status"));
    }

    private Response responseWithError(String msg) {
        return ResponseUtil.badResponse(msg);
    }

    private Response responseWithCardAlreadyProcessed(long chargeId) {
        return responseWithError(String.format("Card already processed for charge with id %s.", chargeId));
    }

    private Response responseWithChargeNotFound(long chargeId) {
        return ResponseUtil.notFoundResponse(String.format("Parent charge with id %s not found.", chargeId));
    }
}
