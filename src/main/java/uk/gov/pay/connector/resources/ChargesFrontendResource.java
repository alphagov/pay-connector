package uk.gov.pay.connector.resources;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import fj.F;
import fj.data.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.google.common.collect.Lists.newArrayList;
import static fj.data.Either.*;
import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.ok;
import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.math.NumberUtils.isNumber;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.resources.CardResource.AUTHORIZATION_FRONTEND_RESOURCE_PATH;
import static uk.gov.pay.connector.resources.CardResource.CAPTURE_FRONTEND_RESOURCE_PATH;
import static uk.gov.pay.connector.util.LinksBuilder.linksBuilder;
import static uk.gov.pay.connector.util.ResponseUtil.*;

@Path("/")
public class ChargesFrontendResource {

    private static final String CHARGES_FRONTEND_PATH = "/v1/frontend/charges/";
    private static final String GET_CHARGE_FRONTEND_PATH = CHARGES_FRONTEND_PATH + "{chargeId}";
    private static final String PUT_CHARGE_STATUS_FRONTEND_PATH = CHARGES_FRONTEND_PATH + "{chargeId}/status";

    private static final Logger logger = LoggerFactory.getLogger(ChargesFrontendResource.class);
    private final ChargeDao chargeDao;
    private final GatewayAccountDao accountDao;

    public ChargesFrontendResource(ChargeDao chargeDao, GatewayAccountDao accountDao) {
        this.chargeDao = chargeDao;
        this.accountDao = accountDao;
    }

    @GET
    @Path(GET_CHARGE_FRONTEND_PATH)
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
        Optional<Map<String, Object>> maybeCharge = chargeDao.findById(chargeId);
        try {
            return maybeCharge
                    .map(charge -> updateStatus(charge, chargeStatusFrom(newStatusMap.get("new_status").toString())))
                    .orElseGet(() -> responseWithChargeNotFound(logger, chargeId));
        } catch (IllegalArgumentException e) {
            logger.error(e.getMessage(), e);
            return badRequestResponse(logger, e.getMessage());
        }
    }

    @GET
    @Path(CHARGES_FRONTEND_PATH)
    @Produces(APPLICATION_JSON)
    public Response getCharges(@QueryParam("gatewayAccountId") String gatewayAccountId, @Context UriInfo uriInfo) {
        return reduce(validateGatewayAccountReference(gatewayAccountId)
                .bimap(handleError, listTransactions(gatewayAccountId)));
    }

    private boolean invalidInput(Map newStatusMap) {
        return newStatusMap == null || newStatusMap.get("new_status") == null;
    }

    private Response updateStatus(Map<String, Object> charge, ChargeStatus newChargeStatus) {
        String charge_id = charge.get("charge_id").toString();
        if (!isValidStateTransition(charge, newChargeStatus)) {
            return badRequestResponse(logger, "charge with id: " + charge_id + " cant be updated to the new state: " + newChargeStatus.getValue());
        }

        List<ChargeStatus> oldStatuses = newArrayList(CREATED, ENTERING_CARD_DETAILS);
        int rowsUpdated = chargeDao.updateNewStatusWhereOldStatusIn(charge_id, newChargeStatus, oldStatuses);
        if (rowsUpdated == 0) {
            return badRequestResponse(logger, "charge with id: " + charge_id + " cant be updated to the new state: " + newChargeStatus.getValue());
        }
        return noContentResponse();
    }

    private boolean isValidStateTransition(Map<String, Object> charge, ChargeStatus newChargeStatus) {
        return newChargeStatus.equals(ENTERING_CARD_DETAILS) && hasStatus(charge, CREATED, ENTERING_CARD_DETAILS);
    }

    private boolean hasStatus(Map<String, Object> charge, ChargeStatus... states) {
        Object currentStatus = charge.get(STATUS_KEY);
        return Arrays.stream(states)
                .anyMatch(status -> equalsIgnoreCase(status.getValue(), currentStatus.toString()));
    }

    private Response buildOkResponse(@PathParam("chargeId") String chargeId, @Context UriInfo uriInfo, Map<String, Object> charge) {
        URI chargeLocation = locationUriFor(GET_CHARGE_FRONTEND_PATH, uriInfo, chargeId);
        Map<String, Object> responseData = linksBuilder(chargeLocation)
                .addLink("cardAuth", HttpMethod.POST, locationUriFor(AUTHORIZATION_FRONTEND_RESOURCE_PATH, uriInfo, chargeId))
                .addLink("cardCapture", HttpMethod.POST, locationUriFor(CAPTURE_FRONTEND_RESOURCE_PATH, uriInfo, chargeId))
                .appendLinksTo(removeGatewayAccount(charge));

        return ok(responseData).build();
    }

    private F<Boolean, Response> listTransactions(final String gatewayAccountId) {
        return success -> {
            List<Map<String, Object>> charges = chargeDao.findAllBy(gatewayAccountId);
            if (charges.isEmpty()) {
                return accountDao.findById(gatewayAccountId)
                        .map(x -> okResultsResponseFrom(charges))
                        .orElseGet(() -> notFoundResponse(logger, format("account with id %s not found", gatewayAccountId)));
            }
            return okResultsResponseFrom(charges);
        };
    }

    private Response okResultsResponseFrom(List<Map<String, Object>> charges) {
        return ok(ImmutableMap.of("results", charges)).build();
    }

    private static F<String, Response> handleError =
            errorMessage -> badRequestResponse(logger, errorMessage);


    private Either<String, Boolean> validateGatewayAccountReference(String gatewayAccountId) {
        if (isBlank(gatewayAccountId)) {
            return left("missing gateway account reference");
        } else if (!isNumber(gatewayAccountId)) {
            return left(format("invalid gateway account reference %s", gatewayAccountId));
        }
        return right(true);
    }

    private Map<String, Object> removeGatewayAccount(Map<String, Object> charge) {
        charge.remove("gateway_account_id");
        return charge;
    }

    private URI locationUriFor(String path, UriInfo uriInfo, String chargeId) {
        return uriInfo.getBaseUriBuilder()
                .path(path).build(chargeId);
    }
}
