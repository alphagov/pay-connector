package uk.gov.pay.connector.charge.model.telephone;

public class PaymentOutcome {
    
    private String status;
    
    private String code;
    
    private Supplemental supplemental;

    public PaymentOutcome() {
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

    public Supplemental getSupplemental() {
        return supplemental;
    }
}
