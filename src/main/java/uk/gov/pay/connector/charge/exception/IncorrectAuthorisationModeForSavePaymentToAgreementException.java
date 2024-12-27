package uk.gov.pay.connector.charge.exception;

import jakarta.ws.rs.WebApplicationException;

public class IncorrectAuthorisationModeForSavePaymentToAgreementException extends WebApplicationException {

    public IncorrectAuthorisationModeForSavePaymentToAgreementException() {
        super("If [save_payment_instrument_to_agreement] is true, [authorisation_mode] must be [web]");
    }

}
