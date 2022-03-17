package uk.gov.pay.connector.charge.exception;

import javax.ws.rs.WebApplicationException;

import static uk.gov.pay.connector.util.ResponseUtil.badRequestResponse;

public class AgreementIdAndSaveInstrumentMandatoryInputException extends WebApplicationException {
    public AgreementIdAndSaveInstrumentMandatoryInputException(String message) {
        super(badRequestResponse(message));
    }
}
