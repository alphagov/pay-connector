package uk.gov.pay.connector.gateway.adyen.request.json;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Authorise3dsRequestPayload(@JsonProperty("details") Details details) {
    public record Details(@JsonProperty("redirectResult") String redirectResult) {
    }
}
