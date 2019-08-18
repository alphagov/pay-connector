package uk.gov.pay.connector.charge.model.telephone;

import com.fasterxml.jackson.annotation.JsonInclude;
import uk.gov.pay.connector.charge.validation.telephone.ValidPaymentOutcome;

@ValidPaymentOutcome
public class PaymentOutcome {
    
    private String status;
    
    private String code;

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    private Supplemental supplemental;

    public PaymentOutcome() {
    }
    
    public PaymentOutcome(String status) {
        this.status = status;
    }
    
    public PaymentOutcome(String status, String code) {
        this.status = status;
        this.code = code;
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

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Supplemental getSupplemental() {
        return supplemental;
    }

    public void setSupplemental(Supplemental supplemental) {
        this.supplemental = supplemental;
    }
}
