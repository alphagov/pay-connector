package uk.gov.pay.connector.gateway.adyen.request.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.pay.connector.gateway.adyen.response.json.BrowserInfo;

import java.util.HashMap;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthoriseRequestPayload(
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
        HashMap<String,String> additionalData,
        @JsonProperty("browserInfo")
        BrowserInfo browserInfo,
        @JsonProperty("origin")
        String origin,
        @JsonProperty("shopperEmail")
        String shopperEmail,
        @JsonProperty("shopperIP")
        String shopperIP
) {
}
