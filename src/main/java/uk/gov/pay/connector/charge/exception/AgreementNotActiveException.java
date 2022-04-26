package uk.gov.pay.connector.charge.exception;
import javax.ws.rs.WebApplicationException;

public class AgreementNotActiveException extends WebApplicationException {
    public AgreementNotActiveException(String message) {
        super(message);
    }
}
