package uk.gov.pay.connector.gateway.stripe.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.ZonedDateTime;

import static uk.gov.pay.connector.util.DateTimeUtils.toUTCZonedDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StripeNotification {

    @JsonProperty("id")
    private String id;
    @JsonProperty("type")
    private String type;
    @JsonProperty("account")
    private String account;
    @JsonProperty("created")
    private Long created;
    @JsonProperty("data")
    private StripeEventData data;

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public StripeEventData getData() {
        return data;
    }

    public String getObject() {
        return data.getObject().toString();
    }

    public String getAccount() {
        return account;
    }

    public ZonedDateTime getCreated() {
        return toUTCZonedDateTime(created);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private class StripeEventData {

        /**
         * Type is JsonNode as object type (source, charge, and so on) varies depending
         * on the notification type received from Stripe
         */
        @JsonProperty("object")
        JsonNode object;

        public StripeEventData() {
        }

        public JsonNode getObject() {
            return object;
        }

        @Override
        public String toString() {
            return "StripeEventData {" +
                    "object='" + object + "'" +
                    '}';
        }
    }

    @Override
    public String toString() {
        // do not add `data` to toString() as it can contain PII (personally identifiable information)
        return "StripeNotification {" +
                "id='" + id + "'" +
                ", type='" + type + "'" +
                '}';
    }
}
