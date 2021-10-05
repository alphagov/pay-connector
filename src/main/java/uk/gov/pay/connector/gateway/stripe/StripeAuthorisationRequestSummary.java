package uk.gov.pay.connector.gateway.stripe;

import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary;

import static uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary.Presence.NOT_PRESENT;
import static uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary.Presence.PRESENT;

public class StripeAuthorisationRequestSummary implements AuthorisationRequestSummary {

    private final Presence billingAddress;
    private final String ipAddress;

    public StripeAuthorisationRequestSummary(AuthCardDetails authCardDetails) {
        billingAddress = authCardDetails.getAddress().map(address -> PRESENT).orElse(NOT_PRESENT);
        ipAddress = authCardDetails.getIpAddress().orElse(null);
    }

    @Override
    public Presence billingAddress() {
        return billingAddress;
    }
    
    @Override
    public String ipAddress() { 
        return ipAddress; 
    }

}
