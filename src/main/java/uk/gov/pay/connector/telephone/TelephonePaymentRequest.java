package uk.gov.pay.connector.telephone;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TelephonePaymentRequest {

    @JsonProperty("account_id")
    private Long accountId;

    @JsonProperty("stripe_id")
    private String stripeId;

    public TelephonePaymentRequest() {
    }

    public TelephonePaymentRequest(Long accountId, String stripeChargeId) {
        this.accountId = accountId;
        this.stripeId = stripeChargeId;
    }

    public String getStripeId() {
        return stripeId;
    }

    public Long getAccountId() {
        return accountId;
    }
}
