package uk.gov.pay.connector.gateway.model.request.records;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NullMarked;
import uk.gov.pay.connector.gateway.adyen.request.json.AdyenGooglePayPaymentMethod;
import uk.gov.pay.connector.gateway.adyen.request.json.Amount;
import uk.gov.pay.connector.gateway.adyen.response.json.AdyenBrowserInfo;

@NullMarked
public record AdyenGooglePayAuthorisePayload(
        @JsonProperty("merchantAccount")
        @NotNull
        String merchantAccount,

        @JsonProperty("store")
        @NotNull
        String store,

        @JsonProperty("reference")
        @NotNull
        String reference,

        @JsonProperty("amount")
        @NotNull
        Amount amount,

        @JsonProperty("paymentMethod")
        @NotNull
        AdyenGooglePayPaymentMethod paymentMethod,

        @JsonProperty("browserInfo")
        @NotNull       
        AdyenBrowserInfo browserInfo,

        @JsonProperty("returnUrl")
        String returnUrl
) implements GooglePayAuthoriseRequest, AdyenAuthoriseRequest  {
    
    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
    
}
