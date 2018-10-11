package uk.gov.pay.connector.junit;

final class DropwizardJUnitRunnerException extends RuntimeException {

    DropwizardJUnitRunnerException(String message) {
        super(message);
    }

    DropwizardJUnitRunnerException(Throwable cause) {
        super(cause);
    }
}
