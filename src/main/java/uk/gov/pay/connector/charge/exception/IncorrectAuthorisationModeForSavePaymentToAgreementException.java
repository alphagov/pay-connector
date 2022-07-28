package uk.gov.pay.connector.charge.exception;

import javax.ws.rs.WebApplicationException;

import static uk.gov.pay.connector.util.ResponseUtil.badRequestResponse;

public class IncorrectAuthorisationModeForSavePaymentToAgreementException extends WebApplicationException {

    public IncorrectAuthorisationModeForSavePaymentToAgreementException() {
        super(badRequestResponse("If [save_payment_instrument_to_agreement] is true, [authorisation_mode] must be [web]"));
    }

}
