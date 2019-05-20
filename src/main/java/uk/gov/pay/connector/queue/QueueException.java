package uk.gov.pay.connector.queue;


public class QueueException extends Exception {

    public QueueException(){
        
    }
    
    public QueueException(String message) {
        super(message);
    }
}
