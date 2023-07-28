package uk.gov.pay.connector.client.ledger.exception;

import javax.ws.rs.core.Response;

public class LedgerException extends RuntimeException {
    private Integer status;

    public LedgerException(Response response) {
        super(response.toString());
        status = response.getStatus();
    }

    public LedgerException(Exception exception) {
        super(exception);
    }

    @Override
    public String toString() {
        return "LedgerException{" +
                "status=" + status +
                ", message=" + getMessage() +
                '}';
    }
    
    public Integer getStatus() {
        return status;
    }
}
