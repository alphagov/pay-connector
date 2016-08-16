package uk.gov.pay.connector.service;

public interface BaseResponse {

    String getTransactionId();

    String getErrorCode();

    String getErrorMessage();
}
