package uk.gov.pay.connector.token.model.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;

import java.util.Objects;

public class TokenResponse {

    @JsonProperty("used")
    private final boolean used;

    @JsonProperty("charge")
    private final ChargeEntity charge;

    public TokenResponse(boolean used, ChargeEntity charge) {
        this.used = used;
        this.charge = Objects.requireNonNull(charge);
    }

}
