package uk.gov.pay.connector.resources;

import com.fasterxml.jackson.annotation.JsonView;
import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.TokenDao;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.util.Optional;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.util.ResponseUtil.*;

@Path("/")
public class SecurityTokensResource {
    public static final String CHARGE_TOKEN_PATH = "/v1/frontend/tokens/{chargeTokenId}";
    public static final String GET_CHARGE_BY_TOKEN_PATH = CHARGE_TOKEN_PATH+ "/charge";

    private final Logger logger = LoggerFactory.getLogger(SecurityTokensResource.class);
    private final TokenDao tokenDao;
    private ChargeDao chargeDao;

    @Inject
    public SecurityTokensResource(TokenDao tokenDao, ChargeDao chargeDao) {
        this.tokenDao = tokenDao;
        this.chargeDao = chargeDao;
    }

    @GET
    @Path(GET_CHARGE_BY_TOKEN_PATH)
    @Produces(APPLICATION_JSON)
    @JsonView(GatewayAccountEntity.Views.PartialView.class)
    public Response getChargeForToken(@PathParam("chargeTokenId") String chargeTokenId) {
        logger.debug("get charge for token {}", chargeTokenId);
        Optional<ChargeEntity> chargeOpt = chargeDao.findByTokenId(chargeTokenId);
        return chargeOpt
                .map(charge -> successResponseWithEntity(charge))
                .orElseGet(() -> notFoundResponse("Token invalid!"));
    }

    @DELETE
    @Path(CHARGE_TOKEN_PATH)
    @Transactional
    public Response deleteToken(@PathParam("chargeTokenId") String chargeTokenId) {
        logger.debug("delete({})", chargeTokenId);
        tokenDao.findByTokenId(chargeTokenId)
                .ifPresent(tokenDao::remove);
        return noContentResponse();
    }
}
