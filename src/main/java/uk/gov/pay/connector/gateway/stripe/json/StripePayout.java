package uk.gov.pay.connector.gateway.stripe.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class StripePayout {
    String id;
    Long amount;
    Long arrivalDate;
    Long created;
    String status;

    public String getId() {
        return id;
    }

    public Long getAmount() {
        return amount;
    }

    public Long getArrivalDate() {
        return arrivalDate;
    }

    public Long getCreated() {
        return created;
    }

    public String getStatus() {
        return status;
    }
}
