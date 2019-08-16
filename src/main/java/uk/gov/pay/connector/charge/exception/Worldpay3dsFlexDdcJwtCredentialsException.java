package uk.gov.pay.connector.charge.exception;

import java.util.Set;

import static java.lang.String.format;

public class Worldpay3dsFlexDdcJwtCredentialsException extends ConflictWebApplicationException {

    public Worldpay3dsFlexDdcJwtCredentialsException(Long accountId, Set<String> missingCredentials) {
        super(format("Cannot generate Worldpay 3ds Flex DDC JWT for account %s because the following credentials are unavailable: %s",
                accountId,  missingCredentials));
    }
}
