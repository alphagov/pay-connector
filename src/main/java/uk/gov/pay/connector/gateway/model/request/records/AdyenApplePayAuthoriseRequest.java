package uk.gov.pay.connector.gateway.model.request.records;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NullMarked;
import uk.gov.pay.connector.gateway.adyen.request.json.AdyenApplePayPaymentMethod;
import uk.gov.pay.connector.gateway.adyen.request.json.Amount;

@NullMarked
public record AdyenApplePayAuthoriseRequest(
        @JsonProperty("merchantAccount")
        String merchantAccount,
        
        @JsonProperty("store")
        String store,
        
        @JsonProperty("reference")
        String reference,
        
        @JsonProperty("amount")
        Amount amount,
        
        @JsonProperty("paymentMethod")
        AdyenApplePayPaymentMethod paymentMethod,
        
        @JsonProperty("returnUrl")
        String returnUrl
) {
}
