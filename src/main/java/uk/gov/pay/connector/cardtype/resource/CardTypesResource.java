package uk.gov.pay.connector.cardtype.resource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import uk.gov.pay.connector.cardtype.dao.CardTypeDao;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.util.ResponseUtil.successResponseWithEntity;

@Path("/")
public class CardTypesResource {

    private final CardTypeDao cardTypeDao;

    @Inject
    public CardTypesResource(CardTypeDao cardTypeDao) {
        this.cardTypeDao = cardTypeDao;
    }

    @GET
    @Path("/v1/api/card-types")
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "List all card types",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(implementation = CardTypesResponse.class)))
            }
    )
    public Response getCardTypes() {
        return successResponseWithEntity(CardTypesResponse.of(cardTypeDao.findAll()));
    }
}
