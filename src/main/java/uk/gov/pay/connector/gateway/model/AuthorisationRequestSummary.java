package uk.gov.pay.connector.gateway.model;

import uk.gov.pay.connector.paymentprocessor.model.Exemption3ds;
import uk.gov.service.payments.commons.model.AgreementPaymentType;

import java.util.Optional;

import static uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary.Presence.NOT_APPLICABLE;

public interface AuthorisationRequestSummary {
    
    enum Presence {
        PRESENT, NOT_PRESENT, NOT_APPLICABLE
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
    }
    
    default Presence setUpAgreement() { return NOT_APPLICABLE; }

    default boolean corporateCard() {
        return false;
    }

    default Optional<Boolean> corporateExemptionRequested() {
        return Optional.empty();
    }

    default Optional<Exemption3ds> corporateExemptionResult() {
        return Optional.empty();
    }
    
    default Presence email() { 
        return NOT_APPLICABLE; 
    }
    
    default Optional<AgreementPaymentType> agreementPaymentType() { 
        return Optional.empty();
    }

}
