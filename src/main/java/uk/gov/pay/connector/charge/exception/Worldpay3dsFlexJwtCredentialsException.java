package uk.gov.pay.connector.charge.exception;

import static java.lang.String.format;

public class Worldpay3dsFlexJwtCredentialsException extends InternalServerErrorException {

    public Worldpay3dsFlexJwtCredentialsException(Long accountId, String missingCredential) {
        super(format("Cannot generate Worldpay 3ds Flex JWT for account %s because the following credential is unavailable: %s",
                accountId,  missingCredential));
    }
}
