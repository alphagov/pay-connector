package uk.gov.pay.connector.charge.model.telephone;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Optional;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class Supplemental {

    @JsonProperty
    private String errorCode;

    @JsonProperty
    private String
            errorMessage;

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
