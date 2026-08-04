package uk.gov.pay.connector.gateway.adyen.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public record AdyenTokenEventData (

    @JsonProperty("merchantAccount")
    String merchantAccount,

    @JsonProperty("storedPaymentMethodId")
    String storedPaymentMethodId,

    @JsonProperty("type")
    String type,

    @JsonProperty("operation")
    String operation,

    @JsonProperty("shopperReference")
    String shopperReference
) {}
