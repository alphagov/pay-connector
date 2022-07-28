package uk.gov.pay.connector.charge.exception;

import javax.ws.rs.WebApplicationException;

import static uk.gov.pay.connector.util.ResponseUtil.badRequestResponse;

public class SavePaymentInstrumentToAgreementRequiresAgreementIdException extends WebApplicationException {

    public SavePaymentInstrumentToAgreementRequiresAgreementIdException() {
        super(badRequestResponse("If [save_payment_instrument_to_agreement] is true, [agreement_id] must be specified"));
    }

}
