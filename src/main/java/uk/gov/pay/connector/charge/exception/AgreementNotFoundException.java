package uk.gov.pay.connector.charge.exception;

import uk.gov.service.payments.commons.model.ErrorIdentifier;
import javax.ws.rs.WebApplicationException;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static uk.gov.pay.connector.util.ResponseUtil.buildErrorResponse;

public class AgreementNotFoundException extends WebApplicationException {
    public AgreementNotFoundException(String message) {
        super(buildErrorResponse(NOT_FOUND, ErrorIdentifier.AGREEMENT_NOT_FOUND, message));
    }
}
