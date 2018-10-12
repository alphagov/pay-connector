package uk.gov.pay.connector.gateway.model.response;

public interface BaseCancelResponse extends BaseResponse {

    String getTransactionId();

    CancelStatus cancelStatus();

    enum CancelStatus {
        SUBMITTED, CANCELLED, ERROR
    }


}
