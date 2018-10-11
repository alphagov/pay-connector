package uk.gov.pay.connector.junit;

class PostgresTestDockerException extends RuntimeException {

    PostgresTestDockerException(String message) {
        super(message);
    }

    PostgresTestDockerException(Throwable cause) {
        super(cause);
    }
}
