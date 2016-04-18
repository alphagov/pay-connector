package uk.gov.pay.connector.exception;

import uk.gov.pay.connector.util.ResponseUtil;

import javax.ws.rs.WebApplicationException;

public class ChargeExpiredRuntimeException extends WebApplicationException {
    public ChargeExpiredRuntimeException(String message) {
        super(ResponseUtil.badRequestResponse(message));
    }
}
