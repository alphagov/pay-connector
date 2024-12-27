package uk.gov.pay.connector.charge.exception;

import jakarta.ws.rs.WebApplicationException;

import static java.lang.String.format;
import static uk.gov.pay.connector.util.ResponseUtil.notFoundResponse;

public class PaymentInstrumentNotFoundRuntimeException extends RuntimeException {
    public PaymentInstrumentNotFoundRuntimeException(String externalId) {
        super(format("Payment Instrument with id [%s] not found.", externalId));
    }
}
