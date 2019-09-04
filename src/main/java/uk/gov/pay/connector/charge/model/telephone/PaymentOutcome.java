package uk.gov.pay.connector.charge.model.telephone;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.pay.connector.charge.validation.telephone.ValidPaymentOutcome;

import java.util.Optional;

@ValidPaymentOutcome(message = "Field [payment_outcome] must include a valid status and error code")
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public class PaymentOutcome {
    
    private String status;
    
    @JsonProperty
    private String code;

    @JsonProperty
    private Supplemental supplemental;

    public PaymentOutcome() {
    }
    
    public PaymentOutcome(String status) {
        this.status = status;
    }
    
    public PaymentOutcome(String status, String code, Supplemental supplemental) {
        // For testing deserialization
        this.status = status;
        this.code = code;
        this.supplemental = supplemental;
    }

    public String getStatus() {
        return status;
    }

    @JsonIgnore
    public Optional<String> getCode() {
        return Optional.ofNullable(code);
    }

    public void setCode(String code) {
        this.code = code;
    }

    @JsonIgnore
    public Optional<Supplemental> getSupplemental() {
        return Optional.ofNullable(supplemental);
    }
    
    public void setSupplemental(Supplemental supplemental) {
        this.supplemental = supplemental;
    }
}
