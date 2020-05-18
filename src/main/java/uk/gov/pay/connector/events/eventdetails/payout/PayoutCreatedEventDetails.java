package uk.gov.pay.connector.events.eventdetails.payout;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import uk.gov.pay.commons.api.json.MicrosecondPrecisionDateTimeSerializer;
import uk.gov.pay.connector.events.eventdetails.EventDetails;

import java.time.ZonedDateTime;

public class PayoutCreatedEventDetails extends EventDetails {

    private Long gatewayAccountId;
    private Long amount;
    @JsonSerialize(using = MicrosecondPrecisionDateTimeSerializer.class)
    private ZonedDateTime estimatedArrivalDateInBank;
    private String gatewayStatus;
    private String destinationType;
    private String statementDescriptor;

    public PayoutCreatedEventDetails(Long gatewayAccountId, Long amount, ZonedDateTime estimatedArrivalDateInBank, String gatewayStatus, String destinationType, String statementDescriptor) {
        this.gatewayAccountId = gatewayAccountId;
        this.amount = amount;
        this.estimatedArrivalDateInBank = estimatedArrivalDateInBank;
        this.gatewayStatus = gatewayStatus;
        this.destinationType = destinationType;
        this.statementDescriptor = statementDescriptor;
    }

    public String getGatewayAccountId() {
        return gatewayAccountId.toString();
    }

    public Long getAmount() {
        return amount;
    }

    public ZonedDateTime getEstimatedArrivalDateInBank() {
        return estimatedArrivalDateInBank;
    }

    public String getGatewayStatus() {
        return gatewayStatus;
    }

    public String getDestinationType() {
        return destinationType;
    }

    public String getStatementDescriptor() {
        return statementDescriptor;
    }
}
