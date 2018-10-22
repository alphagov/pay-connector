package uk.gov.pay.connector.exception;

import java.util.Collections;
import java.util.List;

public class ValidationException extends RuntimeException {
    
    private List<String> errors;
    
    public ValidationException(String error) {
        this(Collections.singletonList(error));
    }
    public ValidationException(List<String> errors) {
        this.errors = errors;
    }

    public List<String> getErrors() {
        return errors;
    }
}
