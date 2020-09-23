package uk.gov.pay.connector.refund.model.domain;

import java.util.Objects;

public class Refund {
    private String externalId;
    private Long amount;
    private String userExternalId;
    private String userEmail;
    private String gatewayTransactionId;
    private String chargeExternalId;
    private RefundStatus status;
    private boolean historic;

    public Refund(String externalId, Long amount, RefundStatus status, String userExternalId, String userEmail, String gatewayTransactionId, String chargeExternalId, boolean historic) {
        this.externalId = externalId;
        this.amount = amount;
        this.status = status;
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
                refundEntity.getStatus(),
                refundEntity.getUserExternalId(),
                refundEntity.getUserEmail(),
                refundEntity.getGatewayTransactionId(),
                refundEntity.getChargeExternalId(),
                false
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

    public RefundStatus getStatus() {
        return status;
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
                Objects.equals(status, refund.status) &&
                Objects.equals(gatewayTransactionId, refund.gatewayTransactionId) &&
                Objects.equals(historic, refund.historic) &&
                Objects.equals(chargeExternalId, refund.chargeExternalId) &&
                Objects.equals(gatewayTransactionId, refund.gatewayTransactionId);
    }
}
