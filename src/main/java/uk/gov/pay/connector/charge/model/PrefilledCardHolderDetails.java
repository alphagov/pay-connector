package uk.gov.pay.connector.charge.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.validator.constraints.Length;
import uk.gov.pay.connector.common.model.domain.PrefilledAddress;

import jakarta.validation.Valid;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PrefilledCardHolderDetails {

    @Length(max = 255, message = "Field [cardholder_name] can have a size between 0 and 255")
    @JsonProperty("cardholder_name")
    @Schema(example = "Joe B", description = "prefilled cardholder name")
    private String cardHolderName;

    @JsonProperty("billing_address")
    @Valid
    @Schema(description = "A structure representing the billing address of a card")
    private PrefilledAddress address;

    public Optional<String> getCardHolderName() {
        return Optional.ofNullable(cardHolderName);
    }

    public void setCardHolderName(String cardHolderName) {
        this.cardHolderName = cardHolderName;
    }

    public Optional<PrefilledAddress> getAddress() {
        return Optional.ofNullable(address);
    }

    public void setAddress(PrefilledAddress address) {
        this.address = address;
    }
}
