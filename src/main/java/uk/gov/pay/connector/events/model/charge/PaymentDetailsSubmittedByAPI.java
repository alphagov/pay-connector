package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.charge.exception.ChargeEventNotFoundRuntimeException;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.eventdetails.charge.PaymentDetailsSubmittedByAPIEventDetails;

import java.time.Instant;
import java.time.ZonedDateTime;

public class PaymentDetailsSubmittedByAPI extends PaymentEvent {

    public PaymentDetailsSubmittedByAPI(String serviceId,
                                        boolean live,
                                        String resourceExternalId,
                                        PaymentDetailsSubmittedByAPIEventDetails eventDetails,
                                        Instant timestamp) {
        super(serviceId, live, resourceExternalId, eventDetails, timestamp);
    }

    public static PaymentDetailsSubmittedByAPI from(ChargeEntity charge) {
        ZonedDateTime lastEventDate = charge.getEvents().stream()
                .filter(e -> e.getStatus() == ChargeStatus.fromString(charge.getStatus()))
                .map(ChargeEventEntity::getUpdated)
                .max(ZonedDateTime::compareTo)
                .orElseThrow(() -> new ChargeEventNotFoundRuntimeException(charge.getExternalId()));

        return new PaymentDetailsSubmittedByAPI(
                charge.getServiceId(),
                charge.getGatewayAccount().isLive(),
                charge.getExternalId(),
                PaymentDetailsSubmittedByAPIEventDetails.from(charge),
                lastEventDate.toInstant());
    }

    public static PaymentDetailsSubmittedByAPI from(ChargeEventEntity chargeEventEntity) {
        ChargeEntity charge = chargeEventEntity.getChargeEntity();

        return new PaymentDetailsSubmittedByAPI(
                charge.getServiceId(),
                charge.getGatewayAccount().isLive(),
                charge.getExternalId(),
                PaymentDetailsSubmittedByAPIEventDetails.from(charge),
                chargeEventEntity.getUpdated().toInstant()
        );
    }

    public String getTitle() {
        return "Payment details submitted by API";
    }

    public String getDescription() {
        return "The event happens when the payment details are submitted by API";
    }
}
