package uk.gov.pay.connector.charge.exception;

import static java.lang.String.format;

public class Worldpay3dsFlexJwtPaymentProviderException extends ConflictWebApplicationException {

    public Worldpay3dsFlexJwtPaymentProviderException(Long accountId) {
        super(format("Cannot provide a Worldpay 3ds flex JWT for account %s because the Payment Provider is not Worldpay.",
                accountId));
    }
}
