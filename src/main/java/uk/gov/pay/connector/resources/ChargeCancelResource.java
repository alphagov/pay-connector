package uk.gov.pay.connector.resources;

import com.google.common.collect.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.PayDBIException;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUBMITTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.READY_FOR_CAPTURE;
import static uk.gov.pay.connector.model.domain.ChargeStatus.STATUS_KEY;
import static uk.gov.pay.connector.model.domain.ChargeStatus.SYSTEM_CANCELLED;
import static uk.gov.pay.connector.util.ResponseUtil.badRequestResponse;
import static uk.gov.pay.connector.util.ResponseUtil.responseWithChargeNotFound;

@Path("/")
public class ChargeCancelResource {
    private static final List<ChargeStatus> CANCELLABLE_STATES = ImmutableList.of(
            CREATED, ENTERING_CARD_DETAILS, AUTHORISATION_SUCCESS, AUTHORISATION_SUBMITTED, READY_FOR_CAPTURE
    );

    private ChargeDao chargeDao;
    private final Logger logger = LoggerFactory.getLogger(ChargeCancelResource.class);

    public ChargeCancelResource(ChargeDao chargeDao) {
        this.chargeDao = chargeDao;
    }

    @POST
    @Path("/v1/api/charges/{chargeId}/cancel")
    @Produces(APPLICATION_JSON)
    public Response cancelCharge(@PathParam("chargeId") String chargeId) throws PayDBIException {

        Optional<Map<String, Object>> maybeCharge = chargeDao.findById(chargeId);
        if (!maybeCharge.isPresent()) {
            return responseWithChargeNotFound(logger, chargeId);
        }

        Map<String, Object> charge = maybeCharge.get();
        if (!isCancellable(charge)) {
            return responseWithChargeStatusIncorrect((String) charge.get(STATUS_KEY));
        }

        chargeDao.updateStatus(chargeId, SYSTEM_CANCELLED);
        return Response.noContent().build();
    }

    private Response responseWithChargeStatusIncorrect(String status) {
        return badRequestResponse(logger, format("Cannot cancel a charge with status %s.", status));
    }

    private static boolean isCancellable(Map<String, Object> charge) {
        Object currentStatus = charge.get(STATUS_KEY);
        return currentStatus != null &&
                CANCELLABLE_STATES.stream()
                        .anyMatch(chargeStatus -> equalsIgnoreCase(chargeStatus.getValue(), currentStatus.toString()));
    }
}
