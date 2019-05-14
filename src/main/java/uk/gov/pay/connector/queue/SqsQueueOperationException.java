package uk.gov.pay.connector.queue;


public class SqsQueueOperationException extends RuntimeException {

    public SqsQueueOperationException(String message) {
        super(message);
    }
}
