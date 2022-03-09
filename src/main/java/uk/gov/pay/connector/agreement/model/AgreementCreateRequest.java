package uk.gov.pay.connector.agreement.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.hibernate.validator.constraints.Length;
import javax.validation.constraints.NotNull;
import java.util.Objects;


@JsonIgnoreProperties(ignoreUnknown = true)
public class AgreementCreateRequest {

    @NotNull(message = "Field [reference] cannot be null")
    @Length(min = 1, max = 255, message = "Field [reference] can have a size between 0 and 255")
    @JsonProperty("reference")
    private String reference;

    public AgreementCreateRequest() {
        // for Jackson
    }

    public AgreementCreateRequest(String reference) {
        this.reference = reference;
    }

    public String getReference() {
        return reference;
    }

    @Override
    public boolean equals(Object o) {   
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AgreementCreateRequest that = (AgreementCreateRequest) o;
        return Objects.equals(reference, that.reference);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reference);
    }
}
