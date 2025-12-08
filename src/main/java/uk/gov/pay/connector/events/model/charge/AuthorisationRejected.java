package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.eventdetails.charge.AuthorisationRejectedEventDetails;
import uk.gov.service.payments.commons.model.AuthorisationMode;

import java.time.Instant;

/**
 * Payment has been rejected
 * - action has been aborted or rejected, payment moves to final rejected state
 *
 */
public class AuthorisationRejected extends PaymentEvent {
    private AuthorisationRejected(String serviceId, boolean live, Long gatewayAccountId, String resourceExternalId, Instant timestamp) {
        super(serviceId, live, gatewayAccountId, resourceExternalId, timestamp);
    }

    private AuthorisationRejected(String serviceId, boolean live, Long gatewayAccountId, String resourceExternalId,
                                  AuthorisationRejectedEventDetails eventDetails, Instant timestamp) {
        super(serviceId, live, gatewayAccountId, resourceExternalId, eventDetails, timestamp);
    }

    public static AuthorisationRejected from(ChargeEventEntity chargeEvent) {
        ChargeEntity charge = chargeEvent.getChargeEntity();
        if (charge.getAuthorisationMode() == AuthorisationMode.AGREEMENT) {
            return new AuthorisationRejected(
                    charge.getServiceId(),
                    charge.getGatewayAccount().isLive(),
                    charge.getGatewayAccount().getId(),
                    charge.getExternalId(),
                    AuthorisationRejectedEventDetails.from(charge),
                    chargeEvent.getUpdated().toInstant()
            );
        }
        return new AuthorisationRejected(
                charge.getServiceId(),
                charge.getGatewayAccount().isLive(),
                charge.getGatewayAccount().getId(),
                charge.getExternalId(),
                chargeEvent.getUpdated().toInstant()
        );
    }
}
