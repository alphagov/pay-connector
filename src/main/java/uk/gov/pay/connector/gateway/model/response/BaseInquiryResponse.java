package uk.gov.pay.connector.gateway.model.response;

public interface BaseInquiryResponse extends BaseResponse {

    String getTransactionId();

    String getLastEvent();
}
