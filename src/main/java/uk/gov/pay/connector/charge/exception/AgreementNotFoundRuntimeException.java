package uk.gov.pay.connector.charge.exception;

import static java.lang.String.format;

public class AgreementNotFoundRuntimeException extends RuntimeException {
    public AgreementNotFoundRuntimeException(String externalId) {
        super(format("Agreement with id [%s] not found.", externalId));
    }
}
