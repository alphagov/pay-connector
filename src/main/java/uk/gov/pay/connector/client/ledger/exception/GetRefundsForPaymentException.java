package uk.gov.pay.connector.client.ledger.exception;

import javax.ws.rs.core.Response;

public class GetRefundsForPaymentException extends LedgerException {
    public GetRefundsForPaymentException(Response response) {
        super(response);
    }
}
