package uk.gov.pay.connector.paymentprocessor.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.common.model.api.ExternalChargeState;
import uk.gov.pay.connector.gateway.ChargeQueryResponse;

import java.util.Optional;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public final class GatewayStatusComparison {
    private final ChargeStatus payStatus;
    @Schema(example = "EXTERNAL_SUBMITTED")
    private final String payExternalStatus;
    @JsonProperty("gatewayStatus")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(example = "AUTHORISED")
    private ChargeStatus gatewayStatus;
    private final String chargeId;
    private String rawGatewayResponse;
    private final Charge charge;
    private boolean processed = false;

    public static GatewayStatusComparison from(Charge charge, ChargeQueryResponse reportedStatus) {
        return new GatewayStatusComparison(charge, reportedStatus);
    }

    public static GatewayStatusComparison getEmpty(Charge charge) {
        return new GatewayStatusComparison(charge);
    }

    private GatewayStatusComparison(Charge charge, ChargeQueryResponse gatewayQueryResponse) {
        this(charge);
        gatewayStatus = gatewayQueryResponse.getMappedStatus().orElse(null);
        rawGatewayResponse = gatewayQueryResponse.getRawGatewayResponseOrErrorMessage();
    }

    private GatewayStatusComparison(Charge charge) {
        chargeId = charge.getExternalId();
        payStatus = charge.isHistoric() ? null : ChargeStatus.fromString(charge.getStatus());
        payExternalStatus = charge.getExternalStatus();
        this.charge = charge;
    }

    @JsonIgnore
    public Charge getCharge() {
        return charge;
    }

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(example = "AUTHORISATION SUCCESS")
    public ChargeStatus getPayStatus() {
        return payStatus;
    }

    @JsonIgnore
    public Optional<ChargeStatus> getGatewayStatus() {
        return Optional.ofNullable(gatewayStatus);
    }

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(example = "EXTERNAL_SUBMITTED")
    public ExternalChargeState getGatewayExternalStatus() {
        return gatewayStatus != null ? gatewayStatus.toExternal() : null;
    }

    public String getPayExternalStatus() {
        return payExternalStatus;
    }

    @Schema(example = "2c6vtn9pth38ppbmnt20d57t49")
    public String getChargeId() {
        return chargeId;
    }

    @Schema(example = "Worldpay response ()")
    public String getRawGatewayResponse() {
        return rawGatewayResponse;
    }

    public boolean hasInternalStatusMismatch() {
        return !payStatus.equals(gatewayStatus);
    }

    public boolean hasExternalStatusMismatch() {
        return gatewayStatus != null &&
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
