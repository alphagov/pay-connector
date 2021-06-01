package uk.gov.pay.connector.gateway.worldpay.exception;

import org.eclipse.jetty.http.HttpStatus;

import javax.ws.rs.WebApplicationException;

public class UnexpectedValidateCredentialsResponse extends WebApplicationException {
    
    public UnexpectedValidateCredentialsResponse() {
        super(HttpStatus.INTERNAL_SERVER_ERROR_500);
    }
}
