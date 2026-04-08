package uk.gov.pay.connector.events.eventdetails.charge;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;

public class AuthorisationRejectedWebPaymentEventDetails extends AuthorisationRejectedEventDetails {
    private AuthorisationRejectedWebPaymentEventDetails(String gatewayRejectionReason) {
        super(gatewayRejectionReason);
    }

    public static AuthorisationRejectedWebPaymentEventDetails from(ChargeEntity charge) {
        return new AuthorisationRejectedWebPaymentEventDetails(charge.getGatewayRejectionReason());
    }
}
