package uk.gov.pay.connector.agreement.exception;
import javax.ws.rs.WebApplicationException;

public class AgreementNotFoundException extends WebApplicationException {

    public AgreementNotFoundException(String message) {
        super(message);
    }

}
