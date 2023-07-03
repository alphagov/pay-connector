package uk.gov.pay.connector.token.resource;

import com.fasterxml.jackson.annotation.JsonView;
import com.google.inject.persist.Transactional;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.dao.ChargeDao;
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

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.util.ResponseUtil.noContentResponse;
import static uk.gov.pay.connector.util.ResponseUtil.notFoundResponse;
import static uk.gov.service.payments.commons.model.AuthorisationMode.MOTO_API;

@Path("/")
@Tag(name = "Secure token")
public class SecurityTokensResource {

    private final Logger logger = LoggerFactory.getLogger(SecurityTokensResource.class);
    private final TokenDao tokenDao;

    @Inject
    public SecurityTokensResource(TokenDao tokenDao) {
        this.tokenDao = tokenDao;
    }

    @GET
    @Path("/v1/frontend/tokens/{chargeTokenId}")
    @Produces(APPLICATION_JSON)
    @JsonView(GatewayAccountEntity.Views.FrontendView.class)
    @Operation(
            summary = "Retrieve secure token",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TokenResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found")
            }
    )
    public Response getToken(@Parameter(example = "a69a2cf3-d5d1-408f-b196-4b716767b507")
                             @PathParam("chargeTokenId") String chargeTokenId) {
        logger.debug("get token {}", chargeTokenId);
        return tokenDao.findByTokenId(chargeTokenId)
                .filter(tokenEntity -> tokenEntity.getChargeEntity().getAuthorisationMode() != MOTO_API)
                .map(tokenEntity -> new TokenResponse(tokenEntity.isUsed(), tokenEntity.getChargeEntity()))
                .map(ResponseUtil::successResponseWithEntity)
                .orElseGet(() -> notFoundResponse("Token invalid!"));
    }

    @POST
    @Path("/v1/frontend/tokens/{chargeTokenId}/used")
    @Produces(APPLICATION_JSON)
    @Transactional
    @Operation(
            summary = "Mark secure token as used",
            responses = {
                    @ApiResponse(responseCode = "204", description = "No content"),
                    @ApiResponse(responseCode = "404", description = "Not found")
            }
    )
    public Response markTokenUsed(@Parameter(example = "a69a2cf3-d5d1-408f-b196-4b716767b507")
                                  @PathParam("chargeTokenId") String chargeTokenId) {
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
    @Operation(
            summary = "Delete secure token",
            responses = {
                    @ApiResponse(responseCode = "204", description = "No content"),
                    @ApiResponse(responseCode = "404", description = "Not found")
            }
    )
    public Response deleteToken(@Parameter(example = "a69a2cf3-d5d1-408f-b196-4b716767b507")
                                @PathParam("chargeTokenId") String chargeTokenId) {
        logger.debug("delete({})", chargeTokenId);
        tokenDao.findByTokenId(chargeTokenId)
                .ifPresent(tokenDao::remove);
        return noContentResponse();
    }
}
