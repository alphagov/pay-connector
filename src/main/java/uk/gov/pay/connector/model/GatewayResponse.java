package uk.gov.pay.connector.model;

import static uk.gov.pay.connector.model.GatewayResponse.ResponseStatus.FAILED;
import static uk.gov.pay.connector.model.GatewayResponse.ResponseStatus.IN_PROGRESS;
import static uk.gov.pay.connector.model.GatewayResponse.ResponseStatus.SUCCEDED;

public abstract class GatewayResponse {


    public enum ResponseStatus {
        IN_PROGRESS,
        SUCCEDED,
        FAILED;
    }
    protected ResponseStatus responseStatus = null;

    public abstract ErrorResponse getError();

    public ResponseStatus getResponseStatus() {
        return responseStatus;
    }

    public boolean isSuccessful() {
        return responseStatus.equals(SUCCEDED);
    }
    public boolean isInProgress() {
        return responseStatus.equals(IN_PROGRESS);
    }
    public boolean isFailed() {
        return responseStatus.equals(FAILED);
    }
}


