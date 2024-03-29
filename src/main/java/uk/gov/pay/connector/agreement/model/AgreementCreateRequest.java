package uk.gov.pay.connector.agreement.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.NotNull;
import java.util.Objects;


@JsonIgnoreProperties(ignoreUnknown = true)
public class AgreementCreateRequest {
    private static final String DESCRIPTION_FIELD = "description";
    private static final String USER_IDENTIFIER_FIELD = "user_identifier";

    @NotNull(message = "Field [reference] cannot be null")
    @Length(min = 1, max = 255, message = "Field [reference] can have a size between 0 and 255")
    @JsonProperty("reference")
    @Schema(example = "Service agreement reference", required = true)
    private String reference;

    @NotNull(message = "Field [" + DESCRIPTION_FIELD + "] cannot be null")
    @Length(min = 1, max = 255, message = "Field [" + DESCRIPTION_FIELD + "] can have a size between 0 and 255")
    @JsonProperty(DESCRIPTION_FIELD)
    @Schema(example = "Description for the paying user describing the purpose of the agreement", required = true)
    private String description;

    @Length(min = 1, max = 255, message = "Field [" + USER_IDENTIFIER_FIELD + "] can have a size between 0 and 255")
    @JsonProperty(USER_IDENTIFIER_FIELD)
    @Schema(example = "reference for the paying user")
    private String userIdentifier;

    public AgreementCreateRequest() {
        // for Jackson
    }

    public AgreementCreateRequest(String reference, String description, String userIdentifier) {
        this.reference = reference;
        this.description = description;
        this.userIdentifier = userIdentifier;
    }

    public String getReference() {
        return reference;
    }

    public String getDescription() {
        return description;
    }

    public String getUserIdentifier() {
        return userIdentifier;
    }

    @Override
    public boolean equals(Object o) {   
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AgreementCreateRequest that = (AgreementCreateRequest) o;
        return Objects.equals(reference, that.reference) &&
                Objects.equals(description, that.description) &&
                Objects.equals(userIdentifier, that.userIdentifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reference, description, userIdentifier);
    }
}
