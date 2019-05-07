package uk.gov.pay.connector.reporting;

import com.stripe.model.Charge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.ChargeResponse;
import uk.gov.pay.connector.charge.service.ChargeService;

import javax.inject.Inject;

public class EventEmitter {
    private static final Logger logger = LoggerFactory.getLogger(EventEmitter.class);

    @Inject
    public EventEmitter() {
    }

    private void emit(Event event) {
        logger.error("Event {}", event);
    }

    public void serviceCreatedCharge(ChargeResponse response) {
        this.emit(new ChargeCreatedEvent(Event.EventTrigger.SERVICE, response));
    }
}
