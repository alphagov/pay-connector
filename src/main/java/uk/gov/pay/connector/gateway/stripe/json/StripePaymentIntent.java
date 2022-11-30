package uk.gov.pay.connector.gateway.stripe.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.pay.connector.gateway.stripe.util.PaymentIntentStringifier;

import java.util.List;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StripePaymentIntent {
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("charges")
    private ChargesCollection chargesCollection;
    
    @JsonProperty("amount_capturable")
    private Long amountCapturable;

    @JsonProperty("status")
    private String status;

    @JsonProperty("last_payment_error")
    private LastPaymentError lastPaymentError;

    public ChargesCollection getChargesCollection() {
        return chargesCollection;
    }

    public String getId() {
        return id;
    }

    public Long getAmountCapturable() {
        return amountCapturable;
    }
    
    public Optional<StripeCharge> getCharge() {
        return chargesCollection != null ? chargesCollection.getCharges().stream().findFirst() :
            Optional.empty();
    }

    public String getStatus() {
        return status;
    }

    public Optional<LastPaymentError> getLastPaymentError() {
        return Optional.ofNullable(lastPaymentError);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChargesCollection {

        @JsonProperty("data")
        private List<StripeCharge> charges;

        public List<StripeCharge> getCharges() {
            return charges;
        }
    }

    public String stringify() {
        return PaymentIntentStringifier.stringify(this);
    }
}
