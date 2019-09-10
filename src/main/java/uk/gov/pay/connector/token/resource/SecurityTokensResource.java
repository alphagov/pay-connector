package uk.gov.pay.connector.token.resource;

import com.fasterxml.jackson.annotation.JsonView;
import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.token.dao.TokenDao;
import uk.gov.pay.connector.token.model.domain.TokenResponse;
import uk.gov.pay.connector.util.ResponseUtil;

import javax.inject.Inject;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import java.util.Optional;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.util.ResponseUtil.noContentResponse;
import static uk.gov.pay.connector.util.ResponseUtil.notFoundResponse;

@Path("/")
public class SecurityTokensResource {

    private final Logger logger = LoggerFactory.getLogger(SecurityTokensResource.class);
    private final TokenDao tokenDao;
    private ChargeDao chargeDao;

    @Inject
    public SecurityTokensResource(TokenDao tokenDao, ChargeDao chargeDao) {
        this.tokenDao = tokenDao;
        this.chargeDao = chargeDao;
    }

    @GET
    @Path("/v1/frontend/tokens/{chargeTokenId}")
    @Produces(APPLICATION_JSON)
    @JsonView(GatewayAccountEntity.Views.FrontendView.class)
    public Response getToken(@PathParam("chargeTokenId") String chargeTokenId) {
        logger.debug("get token {}", chargeTokenId);
        return tokenDao.findByTokenId(chargeTokenId)
                .map(tokenEntity -> new TokenResponse(tokenEntity.isUsed(), tokenEntity.getChargeEntity()))
                .map(ResponseUtil::successResponseWithEntity)
                .orElseGet(() -> notFoundResponse("Token invalid!"));
    }

    @GET
    @Path("/v1/frontend/tokens/{chargeTokenId}/charge")
    @Produces(APPLICATION_JSON)
    @JsonView(GatewayAccountEntity.Views.FrontendView.class)
    public Response getChargeForToken(@PathParam("chargeTokenId") String chargeTokenId) {
        logger.debug("get charge for token {}", chargeTokenId);
        Optional<ChargeEntity> chargeOpt = chargeDao.findByTokenId(chargeTokenId);
        return chargeOpt
                .map(ResponseUtil::successResponseWithEntity)
                .orElseGet(() -> notFoundResponse("Token invalid!"));
    }

    @POST
    @Path("/v1/frontend/tokens/{chargeTokenId}/used")
    @Produces(APPLICATION_JSON)
    @Transactional
    public Response markTokenUsed(@PathParam("chargeTokenId") String chargeTokenId) {
        logger.debug("mark token used for token {}", chargeTokenId);
        return tokenDao.findByTokenId(chargeTokenId)
                .map(tokenEntity -> {
                    tokenEntity.setUsed(true);
                    return noContentResponse();
                })
                .orElseGet(() -> notFoundResponse("Token invalid!"));
    }

    @DELETE
    @Path("/v1/frontend/tokens/{chargeTokenId}")
    @Transactional
    public Response deleteToken(@PathParam("chargeTokenId") String chargeTokenId) {
        logger.debug("delete({})", chargeTokenId);
        tokenDao.findByTokenId(chargeTokenId)
                .ifPresent(tokenDao::remove);
        return noContentResponse();
    }
}
