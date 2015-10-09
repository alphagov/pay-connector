package uk.gov.pay.connector.resources;

import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.TokenDao;
import uk.gov.pay.connector.util.ResponseUtil;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.util.Map;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.util.ResponseUtil.*;

@Path("/")
public class SecurityTokensResource {
    private static final String TOKEN_VALIDATION_PATH = "/v1/frontend/tokens/{chargeTokenId}";

    private final Logger logger = LoggerFactory.getLogger(SecurityTokensResource.class);
    private final TokenDao tokenDao;

    public SecurityTokensResource(TokenDao tokenDao) {
        this.tokenDao = tokenDao;
    }

    @GET
    @Path(TOKEN_VALIDATION_PATH)
    @Produces(APPLICATION_JSON)
    public Response verifyToken(@PathParam("chargeTokenId") String chargeTokenId) {
        logger.debug("verify({})", chargeTokenId);

        return tokenDao
                .findChargeByTokenId(chargeTokenId)
                .map(chargeId -> {
                    Map<Object, Object> tokenResource = ImmutableMap.builder().put("chargeId", chargeId).build();
                    return Response.ok().entity(tokenResource).build();
                }).orElseGet(() ->
                        notFoundResponse(logger, "Token has expired!"));
    }

    @DELETE
    @Path(TOKEN_VALIDATION_PATH)
    public Response deleteToken(@PathParam("chargeTokenId") String chargeTokenId) {
        logger.debug("delete({})", chargeTokenId);
        tokenDao.deleteByTokenId(chargeTokenId);

        return noContentResponse();
    }
}
