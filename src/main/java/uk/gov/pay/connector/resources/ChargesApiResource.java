package uk.gov.pay.connector.resources;

import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.LinksConfig;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.model.api.ExternalChargeStatus;
import uk.gov.pay.connector.util.ResponseUtil;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.google.common.collect.Lists.newArrayList;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.model.api.ExternalChargeStatus.mapFromStatus;
import static uk.gov.pay.connector.model.api.Link.aLink;
import static uk.gov.pay.connector.util.ResponseUtil.badResponse;
import static uk.gov.pay.connector.util.ResponseUtil.fieldsMissingResponse;

@Path("/")
public class ChargesApiResource {
    private static final String CHARGES_API_PATH = "/v1/api/charges/";
    private static final String GET_CHARGE_API_PATH = CHARGES_API_PATH + "{chargeId}";

    private static final String AMOUNT_KEY = "amount";
    private static final String GATEWAY_ACCOUNT_KEY = "gateway_account_id";
    private static final String RETURN_URL_KEY = "return_url";
    private static final String[] REQUIRED_FIELDS = {AMOUNT_KEY, GATEWAY_ACCOUNT_KEY, RETURN_URL_KEY};

    private static final String STATUS_KEY = "status";

    private ChargeDao chargeDao;
    private GatewayAccountDao gatewayAccountDao;
    private LinksConfig linksConfig;
    private Logger logger = LoggerFactory.getLogger(ChargesApiResource.class);

    public ChargesApiResource(ChargeDao chargeDao, GatewayAccountDao gatewayAccountDao, LinksConfig linksConfig) {
        this.chargeDao = chargeDao;
        this.gatewayAccountDao = gatewayAccountDao;
        this.linksConfig = linksConfig;
    }

    @GET
    @Path(GET_CHARGE_API_PATH)
    @Produces(APPLICATION_JSON)
    public Response getCharge(@PathParam("chargeId") String chargeId, @Context UriInfo uriInfo) {
        Optional<Map<String, Object>> maybeCharge = chargeDao.findById(chargeId);
        return maybeCharge
                .map(charge -> {
                    URI documentLocation = chargeLocationFor(uriInfo, chargeId);
                    Map<String, Object> responseData = chargeResponseData(charge, documentLocation.toString(), chargeId);
                    return Response.ok(responseData).build();
                })
                .orElseGet(() -> ResponseUtil.responseWithChargeNotFound(logger, chargeId));
    }

    @POST
    @Path(CHARGES_API_PATH)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createNewCharge(Map<String, Object> chargeRequest, @Context UriInfo uriInfo) {
        Optional<List<String>> missingFields = checkMissingFields(chargeRequest);
        if (missingFields.isPresent()) {
            return fieldsMissingResponse(logger, missingFields.get());
        }

        String gatewayAccountId = chargeRequest.get("gateway_account_id").toString();
        if (gatewayAccountDao.idIsMissing(gatewayAccountId)) {
            return badResponse(logger, "Unknown gateway account: " + gatewayAccountId);
        }

        logger.info("Creating new charge of {}.", chargeRequest);
        String chargeId = chargeDao.saveNewCharge(chargeRequest);

        Optional<Map<String, Object>> maybeCharge = chargeDao.findById(chargeId);

        return maybeCharge
                .map(charge -> {
                    URI newLocation = chargeLocationFor(uriInfo, chargeId);
                    Map<String, Object> responseData = chargeResponseData(charge, newLocation.toString(), chargeId);

                    logger.info("charge = {}", charge);
                    logger.info("responseData = {}", responseData);

                    URI chargeLocation = chargeLocationFor(uriInfo, chargeId);
                    return Response.created(chargeLocation).entity(responseData).build();
                })
                .orElseGet(() -> ResponseUtil.responseWithChargeNotFound(logger, chargeId));
    }

    private Map<String, Object> chargeResponseData(Map<String, Object> charge, String selfUrl, String chargeId) {
        Map<String, Object> externalData = Maps.newHashMap(charge);
        externalData = convertStatusToExternalStatus(externalData);
        return addLinks(externalData, selfUrl, chargeId);
    }

    private Map<String, Object> convertStatusToExternalStatus(Map<String, Object> data) {
        ExternalChargeStatus externalState = mapFromStatus(data.get(STATUS_KEY).toString());
        data.put(STATUS_KEY, externalState.getValue());
        return data;
    }

    private URI chargeLocationFor(UriInfo uriInfo, String chargeId) {
        return uriInfo.getBaseUriBuilder()
                .path(GET_CHARGE_API_PATH).build(chargeId);
    }

    private Optional<List<String>> checkMissingFields(Map<String, Object> inputData) {
        List<String> missing = Arrays.stream(REQUIRED_FIELDS)
                .filter(field -> !inputData.containsKey(field))
                .collect(Collectors.toList());

        return missing.isEmpty()
                ? Optional.<List<String>>empty()
                : Optional.of(missing);
    }

    private Map<String, Object> addLinks(Map<String, Object> charge, String selfUrl, String chargeId) {
        List<Map<String, String>> links = newArrayList(
                aLink(selfUrl, "self", "GET").toMap(),
                aLink(linksConfig.getCardDetailsUrl().replace("{chargeId}", chargeId), "next_url", "GET").toMap()
        );

        charge.put("links", links);
        return charge;
    }
}
