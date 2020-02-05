package uk.gov.pay.connector.charge.model.domain;

import uk.gov.pay.connector.common.model.api.ExternalChargeState;

import java.util.Optional;

public class Charge {

    private String externalId;
    private Long amount;
    private String status;
    private String gatewayTransactionId;
    private Long corporateSurcharge;
    private String externalRefundState;
    private boolean inFlight;

    public Charge(String externalId, Long amount, String status, String gatewayTransactionId, Long corporateSurcharge, String externalRefundState, boolean inFlight) {
        this.externalId = externalId;
        this.amount = amount;
        this.status = status;
        this.gatewayTransactionId = gatewayTransactionId;
        this.corporateSurcharge = corporateSurcharge;
        this.externalRefundState = externalRefundState;
        this.inFlight = inFlight;
    }
    
    public static Charge from(ChargeEntity chargeEntity) {
        return new Charge(
                chargeEntity.getExternalId(),
                chargeEntity.getAmount(),
                chargeEntity.getStatus(),
                chargeEntity.getGatewayTransactionId(),
                chargeEntity.getCorporateSurcharge().orElse(null),
                null, 
                true);
    }

    public String getExternalId() {
        return externalId;
    }

    public Long getAmount() {
        return amount;
    }

    public String getStatus() {
        return status;
    }

    public String getGatewayTransactionId() {
        return gatewayTransactionId;
    }

    public Optional<Long> getCorporateSurcharge() {
        return Optional.ofNullable(corporateSurcharge);
    }

    public boolean isInFlight() {
        return inFlight;
    }

    public String getExternalRefundState() {
        return externalRefundState;
    }
}
