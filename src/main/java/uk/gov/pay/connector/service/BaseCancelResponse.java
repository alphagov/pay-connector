package uk.gov.pay.connector.service;

public interface BaseCancelResponse extends BaseResponse {

    String getTransactionId();

    CancelStatus cancelStatus();

    enum CancelStatus {
        SUBMITTED, CANCELLED, ERROR
    }


}
