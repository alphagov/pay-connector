package uk.gov.pay.connector.model;

import static uk.gov.pay.connector.model.GatewayResponse.ResponseStatus.*;

public abstract class GatewayResponse {

    public enum ResponseStatus {
        IN_PROGRESS,
        SUCCEDED,
        FAILED
    }

    protected ErrorResponse error;
    protected ResponseStatus responseStatus;

    public ErrorResponse getError() {
        return error;
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


