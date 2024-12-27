package uk.gov.pay.connector.charge.exception;

import jakarta.ws.rs.WebApplicationException;

public class IdempotencyKeyUsedException extends WebApplicationException {
    public IdempotencyKeyUsedException() {
        super("The Idempotency-Key has already been used to create a payment");
    }
}
