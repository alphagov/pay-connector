package uk.gov.pay.connector.resources;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;

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
import static uk.gov.pay.connector.model.ChargeStatus.AUTHORIZATION_SUCCESS;
import static uk.gov.pay.connector.model.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.model.ChargeStatus.STATUS_KEY;
import static uk.gov.pay.connector.util.ResponseUtil.badRequestResponse;
import static uk.gov.pay.connector.util.ResponseUtil.responseWithChargeNotFound;

@Path("/")
public class ChargeCaptureResource {

    private ChargeDao chargeDao;
    private final Logger logger = LoggerFactory.getLogger(ChargeCaptureResource.class);

    public ChargeCaptureResource(ChargeDao chargeDao) {
        this.chargeDao = chargeDao;
    }

    @POST
    @Path("/v1/frontend/charges/{chargeId}/capture")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response addCardDetailsForCharge(@PathParam("chargeId") String chargeId) {

        Optional<Map<String, Object>> maybeCharge = chargeDao.findById(chargeId);
        if (!maybeCharge.isPresent()) {
            return responseWithChargeNotFound(logger, chargeId);
        }

        Map<String, Object> charge = maybeCharge.get();
        if (!isAuthorized(charge)) {
            return responseWithChargeStatusIncorrect((String) charge.get(STATUS_KEY));
        }

        chargeDao.updateStatus(chargeId, CAPTURED);
        return Response.noContent().build();
    }

    private Response responseWithChargeStatusIncorrect(String status) {
        return badRequestResponse(logger, format("Cannot capture a charge with status %s.", status));
    }

    private static boolean isAuthorized(Map<String, Object> charge) {
        return AUTHORIZATION_SUCCESS.getValue().equals(charge.get(STATUS_KEY));
    }
}
