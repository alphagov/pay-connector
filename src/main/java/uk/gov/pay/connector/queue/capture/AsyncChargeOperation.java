package uk.gov.pay.connector.queue.capture;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AsyncChargeOperation {
    @JsonProperty("operationKey")
    private AsyncChargeOperationKey operationKey;
    @JsonProperty("chargeId")
    private String chargeId;

    public AsyncChargeOperation() {
    }

    public AsyncChargeOperation(String chargeId, AsyncChargeOperationKey operationKey) {
        this.chargeId = chargeId;
        this.operationKey = operationKey;
    }

    public String getChargeId() {
        return chargeId;
    }

    public AsyncChargeOperationKey getOperationKey() {
        return operationKey;
    }
}
