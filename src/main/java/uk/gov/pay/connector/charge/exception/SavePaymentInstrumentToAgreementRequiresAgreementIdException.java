package uk.gov.pay.connector.charge.exception;

import javax.ws.rs.WebApplicationException;

public class SavePaymentInstrumentToAgreementRequiresAgreementIdException extends WebApplicationException {

    public SavePaymentInstrumentToAgreementRequiresAgreementIdException() {
        super("If [save_payment_instrument_to_agreement] is true, [agreement_id] must be specified");
    }

}
