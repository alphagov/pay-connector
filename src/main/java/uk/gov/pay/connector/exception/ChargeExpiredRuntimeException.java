package uk.gov.pay.connector.exception;

import uk.gov.pay.connector.util.ResponseUtil;

public class ChargeExpiredRuntimeException extends ConnectorRuntimeException {
    public ChargeExpiredRuntimeException(String message) {
        super(ResponseUtil.badRequestResponse(message));
    }
}
