package uk.gov.pay.connector.gateway.adyen.response;

import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;

public record AdyenCancelResponse(
        String transactionId,
        CancelStatus cancelStatus,
        String errorCode,
        String errorMessage
) implements BaseCancelResponse {

    @Override
    public String getTransactionId() {
        return "";
    }

    @Override
    public String getErrorCode() {
        return "";
    }

    @Override
    public String getErrorMessage() {
        return "";
    }
}
