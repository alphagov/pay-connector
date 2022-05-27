package uk.gov.pay.connector.charge.exception;

import javax.ws.rs.WebApplicationException;

import static uk.gov.pay.connector.util.ResponseUtil.badRequestResponse;

public class SavePaymentInstrumentToAgreementRequiresAgreementModeWebException extends WebApplicationException {

    public SavePaymentInstrumentToAgreementRequiresAgreementModeWebException(String message) {
        super(badRequestResponse(message));
    }

}
