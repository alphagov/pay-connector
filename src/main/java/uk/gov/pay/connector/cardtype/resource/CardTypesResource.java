package uk.gov.pay.connector.cardtype.resource;

import com.google.common.collect.ImmutableMap;
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
    private static final String CARD_TYPES_FIELD_NAME = "card_types";

    private final CardTypeDao cardTypeDao;

    @Inject
    public CardTypesResource(CardTypeDao cardTypeDao) {
        this.cardTypeDao = cardTypeDao;
    }

    @GET
    @Path("/v1/api/card-types")
    @Produces(APPLICATION_JSON)
    public Response getCardTypes() {
        return successResponseWithEntity(ImmutableMap.of(CARD_TYPES_FIELD_NAME, cardTypeDao.findAll()));
    }
}
