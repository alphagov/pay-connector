package uk.gov.pay.connector.resources;

import com.google.common.collect.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.util.ResponseBuilder;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.google.common.collect.Lists.newArrayList;
import static javax.ws.rs.HttpMethod.GET;
import static javax.ws.rs.HttpMethod.POST;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.ok;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.resources.ApiPaths.*;
import static uk.gov.pay.connector.util.ResponseUtil.*;

@Path("/")
public class ChargesFrontendResource {
    private static final String PUT_CHARGE_STATUS_FRONTEND_PATH = CHARGE_FRONTEND_PATH + "/status";

    private static final Logger logger = LoggerFactory.getLogger(ChargesFrontendResource.class);
    private final ChargeDao chargeDao;
    private final GatewayAccountDao accountDao;

    public ChargesFrontendResource(ChargeDao chargeDao, GatewayAccountDao accountDao) {
        this.chargeDao = chargeDao;
        this.accountDao = accountDao;
    }

    @GET
    @Path(CHARGE_FRONTEND_PATH)
    @Produces(APPLICATION_JSON)
    public Response getCharge(@PathParam("chargeId") String chargeId, @Context UriInfo uriInfo) {
        Optional<Map<String, Object>> maybeCharge = chargeDao.findById(chargeId);
        logger.debug("charge from DB: " + maybeCharge);
        return maybeCharge
                .map(charge -> buildOkResponse(chargeId, uriInfo, charge))
                .orElseGet(() -> responseWithChargeNotFound(logger, chargeId));
    }

    @PUT
    @Path(PUT_CHARGE_STATUS_FRONTEND_PATH)
    @Produces(APPLICATION_JSON)
    public Response updateChargeStatus(@PathParam("chargeId") String chargeId, Map newStatusMap) {
        if (invalidInput(newStatusMap)) {
            return fieldsMissingResponse(logger, ImmutableList.of("new_status"));
        }
        try {
            return updateStatus(chargeId, chargeStatusFrom(newStatusMap.get("new_status").toString()));
        } catch (IllegalArgumentException e) {
            logger.error(e.getMessage(), e);
            return badRequestResponse(logger, e.getMessage());
        }
    }

    private boolean invalidInput(Map newStatusMap) {
        return newStatusMap == null || newStatusMap.get("new_status") == null;
    }

    private Response updateStatus(String chargeId, ChargeStatus newChargeStatus) {
        if (!isValidStateTransition(newChargeStatus)) {
            return badRequestResponse(logger, "charge with id: " + chargeId + " cant be updated to the new state: " + newChargeStatus.getValue());
        }

        List<ChargeStatus> oldStatuses = newArrayList(CREATED, ENTERING_CARD_DETAILS);
        int rowsUpdated = chargeDao.updateNewStatusWhereOldStatusIn(chargeId, newChargeStatus, oldStatuses);
        if (rowsUpdated == 0) {
            return badRequestResponse(logger, "charge with id: " + chargeId + " cant be updated to the new state: " + newChargeStatus.getValue());
        }
        return noContentResponse();
    }

    private boolean isValidStateTransition(ChargeStatus newChargeStatus) {
        return newChargeStatus.equals(ENTERING_CARD_DETAILS);
    }

    private Response buildOkResponse(@PathParam("chargeId") String chargeId, @Context UriInfo uriInfo, Map<String, Object> charge) {
        Map<String, Object> responseData = new ResponseBuilder()
                .withCharge(charge)
                .withoutChargeField("gateway_account_id")
                .withoutChargeField("reference")
                .withLink("self", GET, locationUriFor(CHARGE_FRONTEND_PATH, uriInfo, chargeId))
                .withLink("cardAuth", POST, locationUriFor(FRONTEND_AUTHORIZATION_RESOURCE, uriInfo, chargeId))
                .withLink("cardCapture", POST, locationUriFor(FRONTEND_CAPTURE_RESOURCE, uriInfo, chargeId))
                .build();

        return ok(responseData).build();
    }

    private URI locationUriFor(String path, UriInfo uriInfo, String chargeId) {
        return uriInfo.getBaseUriBuilder()
                .path(path).build(chargeId);
    }
}
