package uk.gov.pay.connector.resources;

import com.fasterxml.jackson.annotation.JsonView;
import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.dao.TokenDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import javax.inject.Inject;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import java.util.Optional;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.util.ResponseUtil.noContentResponse;
import static uk.gov.pay.connector.util.ResponseUtil.notFoundResponse;
import static uk.gov.pay.connector.util.ResponseUtil.successResponseWithEntity;

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
    @Path("/v1/frontend/tokens/{chargeTokenId}/charge")
    @Produces(APPLICATION_JSON)
    @JsonView(GatewayAccountEntity.Views.FrontendView.class)
    public Response getChargeForToken(@PathParam("chargeTokenId") String chargeTokenId) {
        logger.debug("get charge for token {}", chargeTokenId);
        Optional<ChargeEntity> chargeOpt = chargeDao.findByTokenId(chargeTokenId);
        return chargeOpt
                .map(charge -> successResponseWithEntity(charge))
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
