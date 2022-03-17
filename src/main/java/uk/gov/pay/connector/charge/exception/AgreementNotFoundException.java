package uk.gov.pay.connector.charge.exception;

import javax.ws.rs.WebApplicationException;
import static uk.gov.pay.connector.util.ResponseUtil.notFoundResponse;

public class AgreementNotFoundException extends WebApplicationException {
    public AgreementNotFoundException(String message) {
        super(notFoundResponse(message));
    }
}
