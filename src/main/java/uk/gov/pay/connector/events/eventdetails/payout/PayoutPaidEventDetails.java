package uk.gov.pay.connector.events.eventdetails.payout;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import uk.gov.service.payments.commons.api.json.IsoInstantMicrosecondSerializer;

import java.time.Instant;

public class PayoutPaidEventDetails extends PayoutEventWithGatewayStatusDetails {

    @JsonSerialize(using = IsoInstantMicrosecondSerializer.class)
    private Instant paidOutDate;

    public PayoutPaidEventDetails(Instant paidOutDate, String gatewayStatus) {
        super(gatewayStatus);
        this.paidOutDate = paidOutDate;
    }

    public Instant getPaidOutDate() {
        return paidOutDate;
    }
}
