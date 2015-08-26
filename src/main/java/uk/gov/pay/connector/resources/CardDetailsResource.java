package uk.gov.pay.connector.resources;

import com.google.common.base.Optional;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.model.ChargeStatus;
import uk.gov.pay.connector.util.ResponseUtil;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.Map;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("/")
public class CardDetailsResource {

    private ChargeDao chargeDao;

    public CardDetailsResource(ChargeDao chargeDao) {
        this.chargeDao = chargeDao;
    }

    @POST
    @Path("/v1/frontend/charges/{chargeId}/card")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createNewCharge(@PathParam("chargeId") long chargeId, Map<String, Object> cardDetails, @Context UriInfo uriInfo) {
        URI newLocation = uriInfo.
                getBaseUriBuilder().
                path("/todo").build();

        Optional<Map<String, Object>> maybeCharge = Optional.fromNullable(chargeDao.findById(chargeId));
        if( !maybeCharge.isPresent() ) {
            return ResponseUtil.notFoundResponse(String.format("Parent charge with id %s not found.", chargeId));
        } else if( !ChargeStatus.CREATED.toString().equals(maybeCharge.get().get("status")) ) {
            return ResponseUtil.badResponse(String.format("Card already processed for charge with id %s.", chargeId));
        } else {
            return Response.created(newLocation).build();
        }
    }
}
