package uk.gov.pay.connector.reporting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.pay.connector.charge.model.ChargeResponse;

public class ChargeCreatedEvent extends Event {
    private final ChargeResponse charge;

    public ChargeCreatedEvent(EventTrigger eventTrigger, ChargeResponse chargeResponse) {
        super(eventTrigger, EventType.PAYMENT_REQUEST_CREATE);
        this.charge = chargeResponse;
    }

    @Override
    public JsonNode payload() {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.valueToTree(charge);
    }
}
