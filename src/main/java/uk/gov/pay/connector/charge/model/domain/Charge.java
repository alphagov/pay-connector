package uk.gov.pay.connector.charge.model.domain;

import uk.gov.pay.connector.common.model.api.ExternalChargeState;
import uk.gov.pay.connector.paritycheck.LedgerTransaction;

import java.util.Optional;

public class Charge {

    private String externalId;
    private Long amount;
    private String status;
    private String externalStatus;
    private String gatewayTransactionId;
    private Long corporateSurcharge;
    private String refundAvailabilityStatus;
    private boolean historic;

    public Charge(String externalId, Long amount, String status, String externalStatus, String gatewayTransactionId, Long corporateSurcharge, String refundAvailabilityStatus, boolean historic) {
        this.externalId = externalId;
        this.amount = amount;
        this.status = status;
        this.externalStatus = externalStatus;
        this.gatewayTransactionId = gatewayTransactionId;
        this.corporateSurcharge = corporateSurcharge;
        this.refundAvailabilityStatus = refundAvailabilityStatus;
        this.historic = historic;
    }

    public static Charge from(ChargeEntity chargeEntity) {
        ChargeStatus chargeStatus = ChargeStatus.fromString(chargeEntity.getStatus());

        return new Charge(
                chargeEntity.getExternalId(),
                chargeEntity.getAmount(),
                chargeEntity.getStatus(),
                chargeStatus.toExternal().toString(),
                chargeEntity.getGatewayTransactionId(),
                chargeEntity.getCorporateSurcharge().orElse(null),
                null,
                false);
    }

    public static Charge from(LedgerTransaction transaction) {
        String externalRefundState = null;
        String externalStatus = null;

        if (transaction.getRefundSummary() != null ) {
            externalRefundState = transaction.getRefundSummary().getStatus();
        }

        if (transaction.getState() != null) {
            externalStatus = transaction.getState().getStatus();
        }

        return new Charge(
                transaction.getTransactionId(),
                transaction.getAmount(),
                null,
                externalStatus,
                transaction.getGatewayTransactionId(),
                transaction.getCorporateCardSurcharge(),
                externalRefundState,
                true
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

    public String getExternalStatus() {
        return externalStatus;
    }
}
