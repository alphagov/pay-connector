package uk.gov.pay.connector.resources;

import com.google.common.collect.ImmutableMap;
import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.TokenDao;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.util.Map;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.util.ResponseUtil.noContentResponse;
import static uk.gov.pay.connector.util.ResponseUtil.notFoundResponse;

@Path("/")
public class SecurityTokensResource {
    private static final String TOKEN_VALIDATION_PATH = "/v1/frontend/tokens/{chargeTokenId}";

    private final Logger logger = LoggerFactory.getLogger(SecurityTokensResource.class);
    private final TokenDao tokenDao;
    private ChargeDao chargeDao;

    @Inject
    public SecurityTokensResource(TokenDao tokenDao, ChargeDao chargeDao) {
        this.tokenDao = tokenDao;
        this.chargeDao = chargeDao;
    }

    @GET
    @Path(TOKEN_VALIDATION_PATH)
    @Produces(APPLICATION_JSON)
    public Response verifyToken(@PathParam("chargeTokenId") String chargeTokenId) {
        logger.debug("verify({})", chargeTokenId);

        return tokenDao.findByTokenId(chargeTokenId)
                .map(token -> {
                    String externalId = chargeDao.findById(token.getChargeEntity().getId()).get().getExternalId();
                    Map<Object, Object> tokenResource = ImmutableMap.builder().put("chargeId", externalId).build();
                    return Response.ok().entity(tokenResource).build();
                }).orElseGet(() ->
                        notFoundResponse(logger, "Token has expired!"));
    }

    @DELETE
    @Path(TOKEN_VALIDATION_PATH)
    @Transactional
    public Response deleteToken(@PathParam("chargeTokenId") String chargeTokenId) {
        logger.debug("delete({})", chargeTokenId);
        tokenDao.findByTokenId(chargeTokenId)
                .ifPresent(tokenDao::remove);
        return noContentResponse();
    }
}
