package uk.gov.pay.connector.charge.exception;

import static java.lang.String.format;

public class Worldpay3dsFlexDdcJwtPaymentProviderException extends ConflictWebApplicationException {

    public Worldpay3dsFlexDdcJwtPaymentProviderException(Long accountId) {
        super(format("Cannot provide a Worldpay 3ds flex DDC JWT for account %s because the Payment Provider is not Worldpay.",
                accountId));
    }
}
