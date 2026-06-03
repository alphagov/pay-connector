package uk.gov.pay.connector.gateway.adyen.response;

import uk.gov.pay.connector.gateway.adyen.response.json.CancelResponseBody;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;

public record AdyenCancelResponse(
        String transactionId,
        CancelStatus cancelStatus,
        String errorCode,
        String errorMessage
) implements BaseCancelResponse {

    public static AdyenCancelResponse from(CancelResponseBody cancelResponseBody) {
        return new AdyenCancelResponse(
                cancelResponseBody.paymentPspReference(),
                CancelStatus.SUBMITTED,
                "",
                "");
    }

    @Override
    public String getTransactionId() {
        return transactionId;
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
