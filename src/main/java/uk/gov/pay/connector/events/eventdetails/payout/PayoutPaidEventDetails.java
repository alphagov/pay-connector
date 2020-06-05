package uk.gov.pay.connector.events.eventdetails.payout;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import uk.gov.pay.commons.api.json.MicrosecondPrecisionDateTimeSerializer;

import java.time.ZonedDateTime;

public class PayoutPaidEventDetails extends PayoutEventWithGatewayStatusDetails {

    @JsonSerialize(using = MicrosecondPrecisionDateTimeSerializer.class)
    private ZonedDateTime paidOutDate;

    public PayoutPaidEventDetails(ZonedDateTime paidOutDate, String gatewayStatus) {
        super(gatewayStatus);
        this.paidOutDate = paidOutDate;
    }

    public ZonedDateTime getPaidOutDate() {
        return paidOutDate;
    }
}
