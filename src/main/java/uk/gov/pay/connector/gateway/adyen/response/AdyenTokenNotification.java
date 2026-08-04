package uk.gov.pay.connector.gateway.adyen.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public record AdyenTokenNotification(

        @JsonProperty("createdAt")
        String createdAt,

        @JsonProperty("eventId")
        String eventId,

        @JsonProperty("environment")
        String environment,

        @JsonProperty("data")
        AdyenTokenEventData data,

        @JsonProperty("type")
        String type
) {
}
