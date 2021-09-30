package uk.gov.pay.connector.wallets.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StripeWalletAuthorisationRequest {
    
    @JsonProperty("payment_method_id")
    String paymentMethodId;

    public StripeWalletAuthorisationRequest() {
    }

    public String getPaymentMethodId() {
        return paymentMethodId;
    }
}
