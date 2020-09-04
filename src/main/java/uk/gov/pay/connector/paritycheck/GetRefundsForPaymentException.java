package uk.gov.pay.connector.paritycheck;

import javax.ws.rs.core.Response;

public class GetRefundsForPaymentException extends LedgerException {
    public GetRefundsForPaymentException(Response response) {
        super(response);
    }
}
