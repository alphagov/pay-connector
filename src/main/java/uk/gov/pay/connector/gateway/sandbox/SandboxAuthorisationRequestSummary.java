package uk.gov.pay.connector.gateway.sandbox;

import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary;

public class SandboxAuthorisationRequestSummary implements AuthorisationRequestSummary {

    private final boolean isCorporateCard;
    
    public SandboxAuthorisationRequestSummary(AuthCardDetails authCardDetails) {
        isCorporateCard = authCardDetails.isCorporateCard();
    }

    @Override
    public boolean corporateCard() {
        return isCorporateCard;
    }

}
