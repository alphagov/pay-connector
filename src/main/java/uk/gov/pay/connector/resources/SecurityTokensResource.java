package uk.gov.pay.connector.resources;

import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.PayDBIException;
import uk.gov.pay.connector.dao.TokenDao;
import uk.gov.pay.connector.model.CardError;
import uk.gov.pay.connector.model.ChargeStatus;
import uk.gov.pay.connector.util.ResponseUtil;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import java.util.Map;
import java.util.Optional;

import static com.google.common.collect.Maps.newHashMap;
import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.model.ChargeStatus.AUTHORIZATION_SUCCESS;
import static uk.gov.pay.connector.model.ChargeStatus.STATUS_KEY;
import static uk.gov.pay.connector.model.SandboxCardNumbers.cardErrorFor;
import static uk.gov.pay.connector.model.SandboxCardNumbers.isInvalidCard;
import static uk.gov.pay.connector.model.SandboxCardNumbers.isValidCard;
import static uk.gov.pay.connector.util.ResponseUtil.badResponse;

@Path("/")
public class SecurityTokensResource {
    public static final String TOKEN_VALIDATION_PATH = "/v1/frontend/tokens/{chargeTokenId}";

    private final Logger logger = LoggerFactory.getLogger(SecurityTokensResource.class);
    private final TokenDao tokenDao;

    public SecurityTokensResource(TokenDao tokenDao) {
        this.tokenDao = tokenDao;
    }

    @GET
    @Path(TOKEN_VALIDATION_PATH)
    @Produces(APPLICATION_JSON)
    public Response verifyToken(@PathParam("chargeTokenId") String chargeTokenId) throws PayDBIException {
        logger.debug("verify({})", chargeTokenId);

        return tokenDao
                .findChargeByTokenId(chargeTokenId)
                .map(chargeId -> {
                    Map<Object, Object> tokenResource = ImmutableMap.builder().put("chargeId", chargeId).build();
                    return Response.ok().entity(tokenResource).build();
                }).orElseGet(() ->
                        ResponseUtil.notFoundResponse(logger, "Token has expired!"));
    }

    @DELETE
    @Path(TOKEN_VALIDATION_PATH)
    public Response deleteToken(@PathParam("chargeTokenId") String chargeTokenId) throws PayDBIException {
        logger.debug("delete({})", chargeTokenId);
        tokenDao.deleteByTokenId(chargeTokenId);

        return Response.noContent().build();
    }
}
