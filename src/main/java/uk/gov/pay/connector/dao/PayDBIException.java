package uk.gov.pay.connector.dao;

public class PayDBIException extends RuntimeException {
    public PayDBIException(String message) {
        super(message);
    }
}
