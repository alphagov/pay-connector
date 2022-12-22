package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.charge.exception.ChargeEventNotFoundRuntimeException;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.eventdetails.charge.PaymentDetailsTakenFromPaymentInstrumentEventDetails;

import java.time.Instant;
import java.time.ZonedDateTime;

public class PaymentDetailsTakenFromPaymentInstrument extends PaymentEvent {

    public PaymentDetailsTakenFromPaymentInstrument(String serviceId,
                                                    boolean live,
                                                    String resourceExternalId,
                                                    PaymentDetailsTakenFromPaymentInstrumentEventDetails eventDetails,
                                                    Instant timestamp) {
        super(serviceId, live, resourceExternalId, eventDetails, timestamp);
    }

    public static PaymentDetailsTakenFromPaymentInstrument from(ChargeEntity charge) {
        ZonedDateTime lastEventDate = charge.getEvents().stream()
                .filter(e -> e.getStatus() == ChargeStatus.fromString(charge.getStatus()))
                .map(ChargeEventEntity::getUpdated)
                .max(ZonedDateTime::compareTo)
                .orElseThrow(() -> new ChargeEventNotFoundRuntimeException(charge.getExternalId()));

        return new PaymentDetailsTakenFromPaymentInstrument(
                charge.getServiceId(),
                charge.getGatewayAccount().isLive(),
                charge.getExternalId(),
                PaymentDetailsTakenFromPaymentInstrumentEventDetails.from(charge),
                lastEventDate.toInstant());
    }

    public static PaymentDetailsTakenFromPaymentInstrument from(ChargeEventEntity chargeEventEntity) {
        ChargeEntity charge = chargeEventEntity.getChargeEntity();

        return new PaymentDetailsTakenFromPaymentInstrument(
                charge.getServiceId(),
                charge.getGatewayAccount().isLive(),
                charge.getExternalId(),
                PaymentDetailsTakenFromPaymentInstrumentEventDetails.from(charge),
                chargeEventEntity.getUpdated().toInstant()
        );
    }

    public String getTitle() {
        return "Payment details taken from payment instrument";
    }

    public String getDescription() {
        return "The event happens when the payment details are taken from a payment instrument during a recurring card payment";
    }
}
