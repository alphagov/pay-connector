package uk.gov.pay.connector.gateway.worldpay.exception;

import org.apache.http.HttpStatus;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;

public class ThreeDsFlexDdcServiceUnavailableException extends WebApplicationException {

    public ThreeDsFlexDdcServiceUnavailableException(ProcessingException exception) {
        super(exception, HttpStatus.SC_SERVICE_UNAVAILABLE);
    }

    public ThreeDsFlexDdcServiceUnavailableException() {
        super(HttpStatus.SC_SERVICE_UNAVAILABLE);
    }
}
