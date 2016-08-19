package uk.gov.pay.connector.service;

public interface BaseInquiryResponse extends BaseResponse {

    String getTransactionId();

    String getLastEvent();
}
