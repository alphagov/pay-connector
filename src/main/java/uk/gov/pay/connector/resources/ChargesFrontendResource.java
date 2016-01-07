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
import static fj.data.Either.*;
import static java.lang.String.format;
import static javax.ws.rs.HttpMethod.GET;
import static javax.ws.rs.HttpMethod.POST;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.ok;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.math.NumberUtils.isNumber;
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

    @GET
    @Path(CHARGES_FRONTEND_PATH)
    @Produces(APPLICATION_JSON)
    public Response getCharges(@QueryParam("gatewayAccountId") String gatewayAccountId,
                               @QueryParam("reference") String reference,
                               @QueryParam("status") String status,
                               @QueryParam("fromDate") String fromDate,
                               @QueryParam("toDate") String toDate,
                               @Context UriInfo uriInfo) {
        return reduce(validateGatewayAccountReference(gatewayAccountId)
                .bimap(handleError, listTransactions(gatewayAccountId, reference, status, fromDate, toDate)));
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

    private F<Boolean, Response> listTransactions(final String gatewayAccountId, final String reference,
                                                  final String status, final String fromDate, final String toDate) {
        return success -> {
            List<Map<String, Object>> charges = chargeDao.findAllBy(gatewayAccountId, reference, status, fromDate, toDate);
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
