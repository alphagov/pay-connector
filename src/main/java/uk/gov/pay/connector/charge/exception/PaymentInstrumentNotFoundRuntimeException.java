package uk.gov.pay.connector.charge.exception;

import static java.lang.String.format;

public class PaymentInstrumentNotFoundRuntimeException extends RuntimeException {
    public PaymentInstrumentNotFoundRuntimeException(String externalId) {
        super(format("Payment Instrument with id [%s] not found.", externalId));
    }
}
