package uk.gov.pay.connector.telephone;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TelephonePaymentRequest {
    
    @JsonProperty("stripe_id")
    private String stripeId;

    public TelephonePaymentRequest() {
    }

    public TelephonePaymentRequest(String stripeChargeId) {
        this.stripeId = stripeChargeId;
    }

    public String getStripeId() {
        return stripeId;
    }
}
