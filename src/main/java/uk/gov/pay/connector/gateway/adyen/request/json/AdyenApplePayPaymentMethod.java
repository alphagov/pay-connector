package uk.gov.pay.connector.gateway.adyen.request.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NullMarked;

@JsonInclude(JsonInclude.Include.NON_NULL)
@NullMarked
public record AdyenApplePayPaymentMethod(
        @JsonProperty("type")
        String type,

        @JsonProperty("applePayToken")
        String applePayToken
) {

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

}
