package uk.gov.pay.connector.resources;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;

import javax.ws.rs.GET;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.Map;
import java.util.Optional;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.ok;
import static uk.gov.pay.connector.resources.CardResource.AUTHORIZATION_FRONTEND_RESOURCE_PATH;
import static uk.gov.pay.connector.resources.CardResource.CAPTURE_FRONTEND_RESOURCE_PATH;
import static uk.gov.pay.connector.util.LinksBuilder.linksBuilder;
import static uk.gov.pay.connector.util.ResponseUtil.responseWithChargeNotFound;

@Path("/")
public class ChargesFrontendResource {

    private static final String CHARGES_FRONTEND_PATH = "/v1/frontend/charges/";
    private static final String GET_CHARGE_FRONTEND_PATH = CHARGES_FRONTEND_PATH + "{chargeId}";

    private final Logger logger = LoggerFactory.getLogger(ChargesFrontendResource.class);
    private final ChargeDao chargeDao;

    public ChargesFrontendResource(ChargeDao chargeDao) {
        this.chargeDao = chargeDao;
    }

    @GET
    @Path(GET_CHARGE_FRONTEND_PATH)
    @Produces(APPLICATION_JSON)
    public Response getCharge(@PathParam("chargeId") String chargeId, @Context UriInfo uriInfo) {
        Optional<Map<String, Object>> maybeCharge = chargeDao.findById(chargeId);
        return maybeCharge
                .map(charge -> {
                    URI chargeLocation = locationUriFor(GET_CHARGE_FRONTEND_PATH, uriInfo, chargeId);

                    Map<String, Object> responseData = linksBuilder(chargeLocation)
                            .addLink("cardAuth", HttpMethod.POST, locationUriFor(AUTHORIZATION_FRONTEND_RESOURCE_PATH, uriInfo, chargeId))
                            .addLink("cardCapture", HttpMethod.POST, locationUriFor(CAPTURE_FRONTEND_RESOURCE_PATH, uriInfo, chargeId))
                            .appendLinksTo(removeGatewayAccount(charge));

                    return ok(responseData).build();
                })
                .orElseGet(() -> responseWithChargeNotFound(logger, chargeId));
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
