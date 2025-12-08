package uk.gov.pay.connector.gateway.model.response;

public interface BaseResponse {

    String getErrorCode();

    String getErrorMessage();

    String toString();
}
