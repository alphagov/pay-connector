package uk.gov.pay.connector.cardtype.resource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import uk.gov.pay.connector.cardtype.dao.CardTypeDao;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
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
