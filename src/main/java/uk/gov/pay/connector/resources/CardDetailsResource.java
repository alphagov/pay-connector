package uk.gov.pay.connector.resources;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.PayDBIException;
import uk.gov.pay.connector.model.ChargeStatus;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import java.util.Map;
import java.util.Optional;

import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.resources.CardDetailsValidator.isValidCardDetails;
import static uk.gov.pay.connector.util.ResponseUtil.badResponse;
import static uk.gov.pay.connector.util.ResponseUtil.responseWithChargeNotFound;

@Path("/")
public class CardDetailsResource {
    private final Logger logger = LoggerFactory.getLogger(CardDetailsResource.class);
    private final ChargeDao chargeDao;

    public CardDetailsResource(ChargeDao chargeDao) {
        this.chargeDao = chargeDao;
    }

    @POST
    @Path("/v1/frontend/charges/{chargeId}/cards")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response addCardDetailsForCharge(@PathParam("chargeId") String chargeId, Map<String, Object> cardDetails) throws PayDBIException {

        Optional<Map<String, Object>> maybeCharge = chargeDao.findById(chargeId);
        if (!maybeCharge.isPresent()) {
            return responseWithChargeNotFound(logger, chargeId);
        } else if (!hasStatusCreated(maybeCharge.get())) {
            return responseWithCardAlreadyProcessed(chargeId);
        }

        if (!isValidCardDetails(cardDetails)) {
            return responseWithInvalidCardDetails();
        }

        chargeDao.updateStatus(chargeId, ChargeStatus.AUTHORIZATION_SUBMITTED);

        //here comes the code for authorization - always successful for the scope of this story.

        chargeDao.updateStatus(chargeId, ChargeStatus.AUTHORIZATION_SUCCESS);

        return Response.noContent().build();

    }

    private boolean hasStatusCreated(Map<String, Object> charge) {
        return ChargeStatus.CREATED.getValue().equals(charge.get("status"));
    }

    private Response responseWithInvalidCardDetails() {
        return badResponse(logger, "Values do not match expected format/length.");
    }

    private Response responseWithCardAlreadyProcessed(String chargeId) {
        return badResponse(logger, format("Card already processed for charge with id %s.", chargeId));
    }

}
