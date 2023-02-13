package uk.gov.pay.connector.gateway.model;

import static uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary.Presence.NOT_APPLICABLE;

public interface AuthorisationRequestSummary {
    
    enum Presence {
        PRESENT, NOT_PRESENT, NOT_APPLICABLE
    }
    
    default Presence exemptionRequest() {
        return NOT_APPLICABLE;
    }

    default Presence billingAddress() {
        return NOT_APPLICABLE;
    }

    default Presence dataFor3ds() {
        return NOT_APPLICABLE;
    }

    default Presence dataFor3ds2() {
        return NOT_APPLICABLE;
    }

    default Presence deviceDataCollectionResult() {
        return NOT_APPLICABLE;
    }

    default String ipAddress() { 
        return null; 
    };
    
    default Presence setUpAgreement() { return NOT_APPLICABLE; }

}
