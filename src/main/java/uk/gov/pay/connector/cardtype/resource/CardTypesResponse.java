package uk.gov.pay.connector.cardtype.resource;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import uk.gov.pay.connector.cardtype.model.domain.CardTypeEntity;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CardTypesResponse {

    @Schema(name = "card_types")
    private final List<CardTypeEntity> cardTypes;

    public CardTypesResponse(List<CardTypeEntity> cardTypeList) {
        this.cardTypes = cardTypeList;
    }

    public static CardTypesResponse of(List<CardTypeEntity> cardTypes) {
        return new CardTypesResponse(cardTypes);
    }

    public List<CardTypeEntity> getCardTypes() {
        return cardTypes;
    }
}
