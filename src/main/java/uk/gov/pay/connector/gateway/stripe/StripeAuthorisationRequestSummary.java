package uk.gov.pay.connector.gateway.stripe;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary;

import static uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary.Presence.NOT_PRESENT;
import static uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary.Presence.PRESENT;

public class StripeAuthorisationRequestSummary implements AuthorisationRequestSummary {

    private final Presence billingAddress;
    private final String ipAddress;
    private final boolean isCorporateCard;
    
    private Presence isSetUpAgreement;

    public StripeAuthorisationRequestSummary(ChargeEntity chargeEntity, AuthCardDetails authCardDetails, boolean isSetUpAgreement) {
        billingAddress = authCardDetails.getAddress().map(address -> PRESENT).orElse(NOT_PRESENT);
        ipAddress = authCardDetails.getIpAddress().orElse(null);
        isCorporateCard = authCardDetails.isCorporateCard();
        this.isSetUpAgreement = isSetUpAgreement ? PRESENT: NOT_PRESENT;
    }

    @Override
    public Presence billingAddress() {
        return billingAddress;
    }
    
    @Override
    public String ipAddress() { 
        return ipAddress; 
    }

    @Override
    public Presence setUpAgreement() {
        return isSetUpAgreement;
    }

    @Override
    public boolean corporateCard() {
        return isCorporateCard;
    }
 
}
