package uk.gov.pay.connector.gateway.stripe.response;

import uk.gov.pay.connector.gateway.model.response.BaseInquiryResponse;

import java.util.StringJoiner;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class StripeQueryResponse implements BaseInquiryResponse {
    private String transactionId;
    private String errorCode;
    private String errorMessage;

    public StripeQueryResponse(String transactionId) {
        this.transactionId = transactionId;
    }

    public StripeQueryResponse(String transactionId, String errorCode, String errorMessage) {
        this.transactionId = transactionId;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    @Override
    public String getTransactionId() {
        return transactionId;
    }

    @Override
    public String getErrorCode() {
        return errorMessage;
    }

    @Override
    public String getErrorMessage() {
        return errorCode;
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(", ", "Stripe query response (", ")");
        if (isNotBlank(getTransactionId())) {
            joiner.add("payment intent ID: " + getTransactionId());
        }
        if (isNotBlank(getErrorCode())) {
            joiner.add("error code: " + getErrorCode());
        }
        if (isNotBlank(getErrorMessage())) {
            joiner.add("error: " + getErrorMessage());
        }
        return joiner.toString();
    }
}
