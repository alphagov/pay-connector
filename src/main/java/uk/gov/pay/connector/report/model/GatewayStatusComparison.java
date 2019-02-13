package uk.gov.pay.connector.report.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.common.model.api.ExternalChargeState;
import uk.gov.pay.connector.gateway.epdq.ChargeQueryResponse;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class GatewayStatusComparison {
    private final ChargeStatus payStatus;
    private ChargeStatus gatewayStatus;
    private final String chargeId;
    private String rawGatewayResponse;
    private final ChargeEntity charge;
    private boolean processed = false;

    public static GatewayStatusComparison from(ChargeEntity charge, ChargeQueryResponse reportedStatus) {
        return new GatewayStatusComparison(charge, reportedStatus);
    }
    
    public static GatewayStatusComparison getEmpty(ChargeEntity charge) {
        return new GatewayStatusComparison(charge);
    }
    
    private GatewayStatusComparison(ChargeEntity charge, ChargeQueryResponse gatewayQueryResponse) {
        chargeId = charge.getExternalId();
        payStatus = ChargeStatus.fromString(charge.getStatus());
        gatewayStatus = gatewayQueryResponse.getMappedStatus().isPresent() ?
                gatewayQueryResponse.getMappedStatus().get() : null;
        rawGatewayResponse = gatewayQueryResponse.getRawGatewayResponse();
        this.charge = charge;
    }
    
    private GatewayStatusComparison(ChargeEntity charge) {
        chargeId = charge.getExternalId();
        payStatus = ChargeStatus.fromString(charge.getStatus());
        this.charge = charge;
    }

    @JsonIgnore
    public ChargeEntity getCharge() {
        return charge;
    }

    @JsonSerialize(using = ToStringSerializer.class)
    public ChargeStatus getPayStatus() {
        return payStatus;
    }

    @JsonSerialize(using = ToStringSerializer.class)
    public ChargeStatus getGatewayStatus() {
        return gatewayStatus;
    }

    @JsonSerialize(using = ToStringSerializer.class)
    public ExternalChargeState getGatewayExternalStatus() {
        return gatewayStatus != null ? gatewayStatus.toExternal() : null;
    }

    @JsonSerialize(using = ToStringSerializer.class)
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
        return gatewayStatus !=null &&
                !payStatus.toExternal().getStatus().equals(gatewayStatus.toExternal().getStatus());
    }

    public void setProcessed(boolean processed) {
        this.processed = processed;
    }

    public boolean isProcessed() {
        return processed;
    }

    @Override
    public String toString() {
        return "GatewayStatusComparison{" +
                "payStatus=" + payStatus +
                ", gatewayStatus=" + gatewayStatus +
                ", chargeId='" + chargeId + '\'' +
                ", rawGatewayResponse='" + rawGatewayResponse + '\'' +
                ", charge=" + charge +
                ", processed=" + processed +
                '}';
    }
}
