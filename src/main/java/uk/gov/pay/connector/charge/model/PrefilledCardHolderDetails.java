package uk.gov.pay.connector.charge.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.hibernate.validator.constraints.Length;
import uk.gov.pay.connector.common.model.domain.PrefilledAddress;

import javax.validation.Valid;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PrefilledCardHolderDetails {
    
    @Length(max = 255, message = "Field [cardholder_name] can have a size between 0 and 255")
    @JsonProperty("cardholder_name")
    private String cardHolderName;

    @JsonProperty("billing_address")
    @Valid
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
