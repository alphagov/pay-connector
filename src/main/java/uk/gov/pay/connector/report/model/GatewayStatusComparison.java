package uk.gov.pay.connector.report.model;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.common.model.api.ExternalChargeState;
import uk.gov.pay.connector.gateway.epdq.ChargeQueryResponse;

public class GatewayStatusComparison {
    private ChargeStatus payStatus;
    private ChargeStatus gatewayStatus;
    private String chargeId;
    private String rawGatewayResponse;
    
    public static GatewayStatusComparison from(ChargeEntity charge, ChargeQueryResponse reportedStatus) {
        return new GatewayStatusComparison(charge, reportedStatus);
    }
    
    private GatewayStatusComparison(ChargeEntity charge, ChargeQueryResponse gatewayQueryResponse) {
        chargeId = charge.getExternalId();
        payStatus = ChargeStatus.fromString(charge.getStatus());
        gatewayStatus = gatewayQueryResponse.getMappedStatus();
        rawGatewayResponse = gatewayQueryResponse.getRawGatewayResponse();
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
}
