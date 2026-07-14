package uk.gov.pay.connector.gateway.worldpay.exception;


import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import org.apache.hc.core5.http.HttpStatus;

public class ThreeDsFlexDdcServiceUnavailableException extends WebApplicationException {

    public ThreeDsFlexDdcServiceUnavailableException(ProcessingException exception) {
        super(exception, HttpStatus.SC_SERVICE_UNAVAILABLE);
    }

    public ThreeDsFlexDdcServiceUnavailableException() {
        super(HttpStatus.SC_SERVICE_UNAVAILABLE);
    }
}
