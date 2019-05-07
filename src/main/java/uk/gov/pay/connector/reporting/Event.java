package uk.gov.pay.connector.reporting;

import com.fasterxml.jackson.databind.JsonNode;

import javax.ws.rs.core.Response;
import java.util.UUID;

public abstract class Event {
    EventTrigger eventTrigger;
    EventType eventType;

    enum ObjectType {
        PAYMENT_REQUEST,
        AGREEMENT,
        CHARGE,
        REFUND,
        SERVICE,
        GATEWAY_ACCOUNT
    }

    enum EventType {
        SERVICE_CREATE(ObjectType.SERVICE),
        SERVICE_DESTROY(ObjectType.SERVICE),
        ACCOUNT_CREATE(ObjectType.GATEWAY_ACCOUNT),
        ACCOUNT_DESTROY(ObjectType.GATEWAY_ACCOUNT),
        PAYMENT_REQUEST_CREATE(ObjectType.PAYMENT_REQUEST),
        CHARGE_START_ENTERING_CARD_DETAILS(ObjectType.CHARGE),
        CHARGE_AUTHORISE(ObjectType.CHARGE),
        CHARGE_3DS_REQUIRED(ObjectType.CHARGE),
        CHARGE_3DS_AUTHORISED(ObjectType.CHARGE),
        CHARGE_AUTHORISATION_REFUSED(ObjectType.CHARGE),
        CHARGE_USER_CANCELLED(ObjectType.CHARGE),
        CHARGE_SERVICE_CANCELLED(ObjectType.CHARGE),
        CHARGE_AUTHORISED(ObjectType.CHARGE),
        CHARGE_CAPTURE_APPROVED(ObjectType.CHARGE);

        private final ObjectType objectType;

        EventType(ObjectType objectType) {
            this.objectType = objectType;
        }
    }

    public enum EventTrigger {
        USER,
        SERVICE,
        GATEWAY,
        GOVUK_PAY,
    }

    public Event(EventTrigger eventTrigger, EventType eventType) {
        this.eventTrigger = eventTrigger;
        this.eventType = eventType;
    }
    
    public abstract JsonNode payload();
}
