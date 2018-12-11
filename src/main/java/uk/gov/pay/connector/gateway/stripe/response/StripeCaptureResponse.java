package uk.gov.pay.connector.gateway.stripe.response;

import org.apache.commons.lang3.StringUtils;
import uk.gov.pay.connector.gateway.model.response.BaseCaptureResponse;

import java.util.StringJoiner;

public class StripeCaptureResponse implements BaseCaptureResponse {

    private final String transactionId;
    private final String errorCode;
    private final String errorMessage;

    public StripeCaptureResponse(String transactionId, String errorCode, String errorMessage) {
        this.transactionId = transactionId;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }    
    
    public StripeCaptureResponse(String transactionId) {
        this.transactionId = transactionId;
        this.errorCode = null;
        this.errorMessage = null;
    }

    @Override
    public String getTransactionId() {
        return transactionId;
    }

    @Override
    public String getErrorCode() {
        return errorCode;
    }

    @Override
    public String getErrorMessage() {
        return errorMessage;
    }
    
    @Override
    public String stringify() {
        StringJoiner joiner = new StringJoiner(", ", "Stripe capture response (", ")");
        if (StringUtils.isNotBlank(transactionId)) {
            joiner.add("Charge gateway transaction id: " + transactionId);
        }
        if (StringUtils.isNotBlank(errorCode)) {
            joiner.add("error code: " + errorCode);
        }
        if (StringUtils.isNotBlank(errorMessage)) {
            joiner.add("error: " + errorMessage);
        }
        return joiner.toString();
    }
}
