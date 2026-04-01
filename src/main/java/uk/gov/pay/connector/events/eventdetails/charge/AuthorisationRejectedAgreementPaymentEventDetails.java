package uk.gov.pay.connector.events.eventdetails.charge;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;

public class AuthorisationRejectedAgreementPaymentEventDetails extends AuthorisationRejectedEventDetails {
    private final boolean canRetry;

    AuthorisationRejectedAgreementPaymentEventDetails(boolean canRetry, String gatewayRejectionReason) {
        super(gatewayRejectionReason);
        this.canRetry = canRetry;
    }

    public static AuthorisationRejectedAgreementPaymentEventDetails from(ChargeEntity charge) {
        return new AuthorisationRejectedAgreementPaymentEventDetails(charge.getCanRetry(), charge.getGatewayRejectionReason());
    }

    public boolean getCanRetry() {
        return canRetry;
    }
}
