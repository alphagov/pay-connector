package uk.gov.pay.connector.gateway.stripe.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.ZonedDateTime;

import static uk.gov.pay.connector.util.DateTimeUtils.toUTCZonedDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class StripePayout {
    String id;
    Long amount;
    Long arrivalDate;
    Long created;
    String status;
    String type;
    String statementDescriptor;

    public StripePayout() {
    }

    public StripePayout(String id, Long amount, Long arrivalDate, Long created, String status, String type, String statementDescriptor) {
        this.id = id;
        this.amount = amount;
        this.arrivalDate = arrivalDate;
        this.created = created;
        this.status = status;
        this.type = type;
        this.statementDescriptor = statementDescriptor;
    }

    public String getId() {
        return id;
    }

    public Long getAmount() {
        return amount;
    }

    public ZonedDateTime getCreated() {
        return toUTCZonedDateTime(created);
    }

    public ZonedDateTime getArrivalDate() {
        return toUTCZonedDateTime(arrivalDate);
    }

    public String getStatus() {
        return status;
    }

    public String getType() {
        return type;
    }

    public String getStatementDescriptor() {
        return statementDescriptor;
    }
}
