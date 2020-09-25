package uk.gov.pay.connector.refund.model.domain;

import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.common.model.api.ExternalRefundStatus;

import java.util.Objects;

public class Refund {
    private String externalId;
    private Long amount;
    private String userExternalId;
    private String userEmail;
    private String gatewayTransactionId;
    private String chargeExternalId;
    private ExternalRefundStatus externalStatus;
    private boolean historic;

    public Refund(String externalId, Long amount,
                  ExternalRefundStatus externalStatus,
                  String userExternalId, String userEmail,
                  String gatewayTransactionId,
                  String chargeExternalId,
                  boolean historic) {
        this.externalId = externalId;
        this.amount = amount;
        this.externalStatus = externalStatus;
        this.userExternalId = userExternalId;
        this.userEmail = userEmail;
        this.gatewayTransactionId = gatewayTransactionId;
        this.chargeExternalId = chargeExternalId;
        this.historic = historic;
    }

    public static Refund from(RefundEntity refundEntity) {
        return new Refund(
                refundEntity.getExternalId(),
                refundEntity.getAmount(),
                refundEntity.getStatus().toExternal(),
                refundEntity.getUserExternalId(),
                refundEntity.getUserEmail(),
                refundEntity.getGatewayTransactionId(),
                refundEntity.getChargeExternalId(),
                false
        );
    }

    public static Refund from(LedgerTransaction ledgerTransaction) {
        return new Refund(
                ledgerTransaction.getTransactionId(),
                ledgerTransaction.getAmount(),
                ExternalRefundStatus.fromPublicStatusLabel(ledgerTransaction.getState().getStatus()),
                ledgerTransaction.getUserExternalId(),
                ledgerTransaction.getUserEmail(),
                ledgerTransaction.getParentTransactionId(),
                ledgerTransaction.getGatewayTransactionId(),
                true
        );
    }

    public boolean isHistoric() {
        return historic;
    }

    public String getChargeExternalId() {
        return chargeExternalId;
    }

    public String getGatewayTransactionId() {
        return gatewayTransactionId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public String getUserExternalId() {
        return userExternalId;
    }

    public ExternalRefundStatus getExternalStatus() {
        return externalStatus;
    }

    public Long getAmount() {
        return amount;
    }

    public String getExternalId() {
        return externalId;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Refund refund = (Refund) obj;
        return Objects.equals(externalId, refund.externalId) &&
                Objects.equals(amount, refund.amount) &&
                Objects.equals(externalStatus, refund.externalStatus) &&
                Objects.equals(gatewayTransactionId, refund.gatewayTransactionId) &&
                Objects.equals(historic, refund.historic) &&
                Objects.equals(chargeExternalId, refund.chargeExternalId) &&
                Objects.equals(gatewayTransactionId, refund.gatewayTransactionId);
    }
}
