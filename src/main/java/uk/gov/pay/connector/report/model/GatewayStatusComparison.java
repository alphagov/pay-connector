package uk.gov.pay.connector.report.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.common.model.api.ExternalChargeState;
import uk.gov.pay.connector.gateway.epdq.ChargeQueryResponse;

public final class GatewayStatusComparison {
    private final ChargeStatus payStatus;
    private final ChargeStatus gatewayStatus;
    private final String chargeId;
    private final String rawGatewayResponse;
    private final ChargeEntity charge;
    private boolean processed;

    public static GatewayStatusComparison from(ChargeEntity charge, ChargeQueryResponse reportedStatus) {
        return new GatewayStatusComparison(charge, reportedStatus);
    }
    
    private GatewayStatusComparison(ChargeEntity charge, ChargeQueryResponse gatewayQueryResponse) {
        
        chargeId = charge.getExternalId();
        payStatus = ChargeStatus.fromString(charge.getStatus());
        gatewayStatus = gatewayQueryResponse.getMappedStatus();
        rawGatewayResponse = gatewayQueryResponse.getRawGatewayResponse();
        this.charge = charge;
    }

    @JsonIgnore
    public ChargeEntity getCharge() {
        return charge;
    }

    public ChargeStatus getPayStatus() {
        return payStatus;
    }

    public ChargeStatus getGatewayStatus() {
        return gatewayStatus;
    }
    
    public ExternalChargeState getGatewayExternalStatus() {
        return gatewayStatus.toExternal();
    }

    public ExternalChargeState getPayExternalStatus() {
        return payStatus.toExternal();
    }

    public String getChargeId() {
        return chargeId;
    }

    public String getRawGatewayResponse() {
        return rawGatewayResponse;
    }

    public boolean hasInternalStatusMismatch() {
        return !payStatus.equals(gatewayStatus); 
    }

    public boolean hasExternalStatusMismatch() {
        return !payStatus.toExternal().getStatus().equals(gatewayStatus.toExternal().getStatus()); 
    }

    public void setProcessed(boolean processed) {
        this.processed = processed;
    }

    public boolean isProcessed() {
        return processed;
    }
}
