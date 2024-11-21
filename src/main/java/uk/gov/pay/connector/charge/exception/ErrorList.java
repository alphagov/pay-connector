package uk.gov.pay.connector.charge.exception;

import java.util.Set;

public class ErrorList extends RuntimeException {
    private final int httpStatus;
    private final Set<ErrorListMapper.Error> errors;

    public ErrorList(int httpStatus, Set<ErrorListMapper.Error> errors) {
        this.httpStatus = httpStatus;
        this.errors = errors;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public Set<ErrorListMapper.Error> getExceptions() {
        return errors;
    }
}
