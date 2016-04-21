package uk.gov.pay.connector.resources;

import com.google.common.collect.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.model.ChargeResponse;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import javax.inject.Inject;
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
import static uk.gov.pay.connector.model.ChargeResponse.Builder.aChargeResponse;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.resources.ApiPaths.*;
import static uk.gov.pay.connector.util.ResponseUtil.*;

@Path("/")
public class ChargesFrontendResource {

    private static final Logger logger = LoggerFactory.getLogger(ChargesFrontendResource.class);
    private static final List<ChargeStatus> CURRENT_STATUSES_ALLOWING_UPDATE_TO_NEW_STATUS = newArrayList(CREATED, ENTERING_CARD_DETAILS);

    private final ChargeDao chargeDao;

    @Inject
    public ChargesFrontendResource(ChargeDao chargeDao) {
        this.chargeDao = chargeDao;
    }

    @GET
    @Path(FRONTEND_CHARGE_API_PATH)
    @Produces(APPLICATION_JSON)
    public Response getCharge(@PathParam("chargeId") String chargeId, @Context UriInfo uriInfo) {

        Optional<ChargeEntity> maybeCharge = chargeDao.findByExternalId(chargeId);
        logger.debug("charge from DB: " + maybeCharge);

        return maybeCharge
                .map(charge -> Response.ok(buildChargeResponse(uriInfo, charge)).build())
                .orElseGet(() -> responseWithChargeNotFound(chargeId));
    }

    @PUT
    @Path(FRONTEND_CHARGE_STATUS_API_PATH)
    @Produces(APPLICATION_JSON)
    public Response updateChargeStatus(@PathParam("chargeId") String chargeId, Map newStatusMap) {
        if (invalidInput(newStatusMap)) {
            return fieldsMissingResponse(ImmutableList.of("new_status"));
        }
        try {
            return updateStatus(chargeId, chargeStatusFrom(newStatusMap.get("new_status").toString()));
        } catch (IllegalArgumentException e) {
            logger.error(e.getMessage(), e);
            return badRequestResponse(e.getMessage());
        }
    }

    private boolean invalidInput(Map newStatusMap) {
        return newStatusMap == null || newStatusMap.get("new_status") == null;
    }

    private Response updateStatus(String chargeId, ChargeStatus newChargeStatus) {
        if (!isValidStateTransition(newChargeStatus)) {
            return badRequestResponse("charge with id: " + chargeId + " cant be updated to the new state: " + newChargeStatus.getValue());
        }
        return chargeDao.findByExternalId(chargeId)
                .map(chargeEntity -> {
                    if (CURRENT_STATUSES_ALLOWING_UPDATE_TO_NEW_STATUS.contains(chargeStatusFrom(chargeEntity.getStatus()))) {
                        chargeEntity.setStatus(newChargeStatus);
                        chargeDao.mergeAndNotifyStatusHasChanged(chargeEntity);
                        return noContentResponse();
                    }
                    return badRequestResponse("charge with id: " + chargeId + " cant be updated to the new state: " + newChargeStatus.getValue());
                }).get();
    }

    private boolean isValidStateTransition(ChargeStatus newChargeStatus) {
        return newChargeStatus.equals(ENTERING_CARD_DETAILS);
    }

    private ChargeResponse buildChargeResponse(UriInfo uriInfo, ChargeEntity charge) {
        String chargeId = charge.getExternalId();
        return aChargeResponse()
                .withChargeId(chargeId)
                .withAmount(charge.getAmount())
                .withDescription(charge.getDescription())
                .withStatus(charge.getStatus())
                .withGatewayTransactionId(charge.getGatewayTransactionId())
                .withCreatedDate(charge.getCreatedDate())
                .withReturnUrl(charge.getReturnUrl())
                .withLink("self", GET, locationUriFor(FRONTEND_CHARGE_API_PATH, uriInfo, chargeId))
                .withLink("cardAuth", POST, locationUriFor(FRONTEND_CHARGE_AUTHORIZE_API_PATH, uriInfo, chargeId))
                .withLink("cardCapture", POST, locationUriFor(FRONTEND_CHARGE_CAPTURE_API_PATH, uriInfo, chargeId)).build();
    }

    private URI locationUriFor(String path, UriInfo uriInfo, String chargeId) {
        return uriInfo.getBaseUriBuilder()
                .path(path)
                .build(chargeId);
    }
}
