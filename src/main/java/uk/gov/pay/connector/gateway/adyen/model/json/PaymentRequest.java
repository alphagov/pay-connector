package uk.gov.pay.connector.gateway.adyen.model.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentRequest(
        @JsonProperty("amount")
        Amount amount,
        @JsonProperty("billingAddress")
        BillingAddress billingAddress,
        @JsonProperty("merchantAccount")
        String merchantAccount,
        @JsonProperty("paymentMethod")
        PaymentMethod paymentMethod,
        @JsonProperty("reference")
        String reference,
        @JsonProperty("returnUrl")
        String returnUrl,
        @JsonProperty("shopperInteraction")
        String shopperInteraction,
        @JsonProperty("store")
        String store,
        @JsonProperty("channel")
        String channel,
        @JsonProperty("additionalData")
        HashMap<String,String> additionalData) {
}
