package uk.gov.pay.connector.resources;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.GatewayAccountDao;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.util.ResponseUtil.badResponse;
import static uk.gov.pay.connector.util.ResponseUtil.fieldsMissingResponse;

@Path("/v1/api/charges")
public class ChargeRequestResource {
    private static final String AMOUNT_KEY = "amount";
    private static final String GATEWAY_ACCOUNT_KEY = "gateway_account_id";

    private static final String[] REQUIRED_FIELDS = {AMOUNT_KEY, GATEWAY_ACCOUNT_KEY};

    private ChargeDao chargeDao;
    private GatewayAccountDao gatewayAccountDao;
    private Logger logger = LoggerFactory.getLogger(ChargeRequestResource.class);

    public ChargeRequestResource(ChargeDao chargeDao, GatewayAccountDao gatewayAccountDao) {
        this.chargeDao = chargeDao;
        this.gatewayAccountDao = gatewayAccountDao;
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createNewCharge(Map<String, Object> chargeRequest, @Context UriInfo uriInfo) {
        Optional<List<String>> missingFields = checkMissingFields(chargeRequest);
        if (missingFields.isPresent()) {
            return fieldsMissingResponse(logger, missingFields.get());
        }

        String gatewayAccountId = chargeRequest.get("gateway_account_id").toString();
        if (gatewayAccountDao.idIsMissing(gatewayAccountId)) {
            return badResponse("Unknown gateway account: " + gatewayAccountId);
        }

        logger.info("Creating new charge of {}.", chargeRequest);
        String chargeId = chargeDao.saveNewCharge(chargeRequest);

        URI newLocation = uriInfo.
                getBaseUriBuilder().
                path(ChargeInfoResource.FIND_CHARGE_BY_ID).build(chargeId);

        Map<String, Object> account = Maps.newHashMap();
        account.put("charge_id", "" + chargeId);
        addSelfLink(newLocation, account);

        return Response.created(newLocation).entity(account).build();
    }

    private Optional<List<String>> checkMissingFields(Map<String, Object> inputData) {
        List<String> missing = new ArrayList<>();
        for (String field : REQUIRED_FIELDS) {
            if (!inputData.containsKey(field)) {
                missing.add(field);
            }
        }
        return missing.isEmpty()
                ? Optional.<List<String>>empty()
                : Optional.of(missing);
    }

    private Map<String, Object> addSelfLink(URI chargeId, Map<String, Object> charge) {
        List<Map<String, Object>> links = ImmutableList.of(ImmutableMap.of("href", chargeId, "rel", "self", "method", "GET"));
        charge.put("links", links);
        return charge;
    }
}
