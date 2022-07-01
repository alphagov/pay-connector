package uk.gov.pay.connector.token.model.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;

import java.util.Objects;

public class TokenResponse {

    @JsonProperty("used")
    @Schema(description = "true or false depending on whether the token has been marked as used or not")
    private final boolean used;

    @JsonProperty("charge")
    @Schema(description = "The charge associated with the token")
    private final ChargeEntity charge;

    public TokenResponse(boolean used, ChargeEntity charge) {
        this.used = used;
        this.charge = Objects.requireNonNull(charge);
    }

}
