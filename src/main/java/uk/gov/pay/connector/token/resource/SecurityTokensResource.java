package uk.gov.pay.connector.token.resource;

import com.google.inject.persist.Transactional;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.token.dao.TokenDao;
import uk.gov.pay.connector.token.exception.TokenNotFoundException;
import uk.gov.pay.connector.token.model.domain.TokenResponse;

import javax.inject.Inject;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.util.ResponseUtil.noContentResponse;
import static uk.gov.pay.connector.util.ResponseUtil.notFoundResponse;
import static uk.gov.service.payments.commons.model.AuthorisationMode.MOTO_API;

@Path("/")
@Tag(name = "Secure token")
public class SecurityTokensResource {

    private final Logger logger = LoggerFactory.getLogger(SecurityTokensResource.class);
    private final TokenDao tokenDao;
    private final ChargeService chargeService;

    @Inject
    public SecurityTokensResource(TokenDao tokenDao, ChargeService chargeService) {
        this.tokenDao = tokenDao;
        this.chargeService = chargeService;
    }

    @GET
    @Path("/v1/frontend/tokens/{chargeTokenId}")
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "Retrieve secure token",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TokenResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found")
            }
    )
    public TokenResponse getToken(@Parameter(example = "a69a2cf3-d5d1-408f-b196-4b716767b507")
                                  @PathParam("chargeTokenId") String chargeTokenId,
                                  @Context UriInfo uriInfo) {
        logger.debug("get token {}", chargeTokenId);
        return tokenDao.findByTokenId(chargeTokenId)
                .filter(tokenEntity -> tokenEntity.getChargeEntity().getAuthorisationMode() != MOTO_API)
                .map(tokenEntity -> new TokenResponse(tokenEntity.isUsed(), chargeService.buildChargeResponse(uriInfo, tokenEntity.getChargeEntity())))
                .orElseThrow(() -> new TokenNotFoundException("Token invalid!"));
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
