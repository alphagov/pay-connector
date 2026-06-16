package uk.gov.pay.connector.gateway.adyen.response;

import org.apache.commons.lang3.StringUtils;
import uk.gov.pay.connector.gateway.adyen.response.json.AdyenError;
import uk.gov.pay.connector.gateway.adyen.response.json.CancelResponseBody;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;

import java.util.StringJoiner;

public record AdyenCancelResponse(
        String transactionId,
        CancelStatus cancelStatus,
        String errorCode,
        String errorType,
        String errorMessage
) implements BaseCancelResponse {

    public static AdyenCancelResponse from(CancelResponseBody cancelResponseBody) {
        return new AdyenCancelResponse(
                cancelResponseBody.paymentPspReference(),
                CancelStatus.SUBMITTED,
                "",
                "",
                "");
    }

    public static AdyenCancelResponse from(AdyenError adyenError) {
        return new AdyenCancelResponse(
                adyenError.pspReference(),
                CancelStatus.ERROR,
                adyenError.errorCode(),
                adyenError.errorType(),
                adyenError.message());
    }

    @Override
    public String getTransactionId() {
        return transactionId();
    }

    @Override
    public String getErrorCode() {
        return errorCode();
    }

    @Override
    public String getErrorMessage() {
        return errorMessage();
    }
    
    public String toString() {
        StringJoiner joiner = new StringJoiner(", ", "Adyen cancel response (", ")");
        joiner.add("PSP reference: " + transactionId);
        joiner.add("Cancel status: " + cancelStatus);
        if (StringUtils.isNotBlank(errorCode)) {
            joiner.add("error code: " + errorCode);
        }
        if (StringUtils.isNotBlank(errorType)) {
            joiner.add("error type: " + errorType);
        }
        if (StringUtils.isNotBlank(errorMessage)) {
            joiner.add("error: " + errorMessage);
        }
        return joiner.toString();
    }
}
