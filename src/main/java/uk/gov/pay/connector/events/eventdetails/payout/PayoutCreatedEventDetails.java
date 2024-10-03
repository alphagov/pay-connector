package uk.gov.pay.connector.events.eventdetails.payout;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import uk.gov.service.payments.commons.api.json.IsoInstantMicrosecondSerializer;

import java.time.Instant;

public class PayoutCreatedEventDetails extends PayoutEventWithGatewayStatusDetails {

    private Long gatewayAccountId;
    private Long amount;
    @JsonSerialize(using = IsoInstantMicrosecondSerializer.class)
    private Instant estimatedArrivalDateInBank;
    private String destinationType;
    private String statementDescriptor;

    public PayoutCreatedEventDetails(Long gatewayAccountId, Long amount, Instant estimatedArrivalDateInBank, String gatewayStatus, String destinationType, String statementDescriptor) {
        super(gatewayStatus);
        this.gatewayAccountId = gatewayAccountId;
        this.amount = amount;
        this.estimatedArrivalDateInBank = estimatedArrivalDateInBank;
        this.destinationType = destinationType;
        this.statementDescriptor = statementDescriptor;
    }

    public String getGatewayAccountId() {
        return gatewayAccountId.toString();
    }

    public Long getAmount() {
        return amount;
    }

    public Instant getEstimatedArrivalDateInBank() {
        return estimatedArrivalDateInBank;
    }

    public String getDestinationType() {
        return destinationType;
    }

    public String getStatementDescriptor() {
        return statementDescriptor;
    }
}
