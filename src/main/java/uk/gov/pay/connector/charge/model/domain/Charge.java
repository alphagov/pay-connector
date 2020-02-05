package uk.gov.pay.connector.charge.model.domain;

import uk.gov.pay.connector.paritycheck.LedgerTransaction;

import java.util.Optional;

public class Charge {

    private String externalId;
    private Long amount;
    private String status;
    private String gatewayTransactionId;
    private Long corporateSurcharge;
    private String refundAvailabilityStatus;
    private boolean historic;

    public Charge(String externalId, Long amount, String status, String gatewayTransactionId, Long corporateSurcharge, String refundAvailabilityStatus, boolean historic) {
        this.externalId = externalId;
        this.amount = amount;
        this.status = status;
        this.gatewayTransactionId = gatewayTransactionId;
        this.corporateSurcharge = corporateSurcharge;
        this.refundAvailabilityStatus = refundAvailabilityStatus;
        this.historic = historic;
    }

    public static Charge from(ChargeEntity chargeEntity) {
        return new Charge(
                chargeEntity.getExternalId(),
                chargeEntity.getAmount(),
                chargeEntity.getStatus(),
                chargeEntity.getGatewayTransactionId(),
                chargeEntity.getCorporateSurcharge().orElse(null),
                null, 
                false);
    }

    public static Charge from(LedgerTransaction transaction) {
        String externalRefundState = null;

        if (transaction.getRefundSummary() != null ) {
            externalRefundState = transaction.getRefundSummary().getStatus();
        }

        return new Charge(
                transaction.getTransactionId(),
                transaction.getAmount(),
                null,
                transaction.getGatewayTransactionId(),
                transaction.getCorporateCardSurcharge(),
                externalRefundState,
                false
        );
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

    public boolean isHistoric() {
        return historic;
    }

    public String getRefundAvailabilityStatus() {
        return refundAvailabilityStatus;
    }
}
