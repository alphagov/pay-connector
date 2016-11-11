package uk.gov.pay.connector.service;

public interface BaseAuthoriseResponse extends BaseResponse {

    String getTransactionId();

    boolean isAuthorised();

}
