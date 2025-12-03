package uk.gov.pay.connector.agreement.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.validator.constraints.Length;

import jakarta.validation.constraints.NotNull;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;


@JsonIgnoreProperties(ignoreUnknown = true)
public record AgreementCreateRequest (
        @NotNull(message = "Field [reference] cannot be null")
        @Length(min = 1, max = 255, message = "Field [reference] can have a size between 1 and 255")
        @JsonProperty("reference")
        @Schema(example = "Service agreement reference", requiredMode = REQUIRED)
        String reference,

        @NotNull(message = "Field [" + DESCRIPTION_FIELD + "] cannot be null")
        @Length(min = 1, max = 255, message = "Field [" + DESCRIPTION_FIELD + "] can have a size between 1 and 255")
        @JsonProperty(DESCRIPTION_FIELD)
        @Schema(example = "Description for the paying user describing the purpose of the agreement", requiredMode = REQUIRED)
        String description,

        @Length(min = 1, max = 255, message = "Field [" + USER_IDENTIFIER_FIELD + "] can have a size between 0 and 255")
        @JsonProperty(USER_IDENTIFIER_FIELD)
        @Schema(example = "reference for the paying user")
        String userIdentifier
) {
    private static final String DESCRIPTION_FIELD = "description";
    private static final String USER_IDENTIFIER_FIELD = "user_identifier";
}
