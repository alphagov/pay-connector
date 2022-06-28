package uk.gov.pay.connector.charge.model.telephone;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Optional;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Supplemental {

    @JsonProperty
    @Schema(example = "E1234")
    private String errorCode;

    @JsonProperty
    @Schema(example = "The payment card does not exist")
    private String errorMessage;

    public Supplemental() {
    }

    public Supplemental(String errorCode, String errorMessage) {
        // For testing deserialization
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    @JsonIgnore
    public Optional<String> getErrorCode() {
        return Optional.ofNullable(errorCode);
    }

    @JsonIgnore
    public Optional<String> getErrorMessage() {
        return Optional.ofNullable(errorMessage);
    }
}
