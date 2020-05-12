package uk.gov.pay.connector.charge.model.domain;

import uk.gov.pay.connector.paritycheck.LedgerTransaction;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;

public class Charge {

    private String externalId;
    private Long amount;
    private String status;
    private String externalStatus;
    private String gatewayTransactionId;
    private Long corporateSurcharge;
    private String refundAvailabilityStatus;
    private String reference;
    private String description;
    private ZonedDateTime createdDate;
    private String email;
    private Long gatewayAccountId;
    private String paymentGatewayName;
    private boolean historic;

    public Charge(String externalId, Long amount, String status, String externalStatus, String gatewayTransactionId,
                  Long corporateSurcharge, String refundAvailabilityStatus, String reference,
                  String description, ZonedDateTime createdDate, String email, Long gatewayAccountId, String paymentGatewayName, boolean historic) {
        this.externalId = externalId;
        this.amount = amount;
        this.status = status;
        this.externalStatus = externalStatus;
        this.gatewayTransactionId = gatewayTransactionId;
        this.corporateSurcharge = corporateSurcharge;
        this.refundAvailabilityStatus = refundAvailabilityStatus;
        this.reference = reference;
        this.description = description;
        this.createdDate = createdDate;
        this.email = email;
        this.gatewayAccountId = gatewayAccountId;
        this.paymentGatewayName = paymentGatewayName;
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
                chargeEntity.getReference().toString(),
                chargeEntity.getDescription(),
                chargeEntity.getCreatedDate(),
                chargeEntity.getEmail(),
                chargeEntity.getGatewayAccount().getId(),
                chargeEntity.getPaymentGatewayName().getName(),
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
                transaction.getReference(),
                transaction.getDescription(),
                ZonedDateTime.parse(transaction.getCreatedDate()),
                transaction.getEmail(),
                Long.valueOf(transaction.getGatewayAccountId()),
                transaction.getPaymentProvider(),
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

    public String getReference() {
        return reference;
    }

    public String getDescription() {
        return description;
    }

    public ZonedDateTime getCreatedDate() {
        return createdDate;
    }

    public String getEmail() {
        return email;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Charge charge = (Charge) obj;
        return Objects.equals(externalId, charge.externalId) &&
                Objects.equals(amount, charge.amount) &&
                Objects.equals(status, charge.status) &&
                Objects.equals(gatewayTransactionId, charge.gatewayTransactionId) &&
                Objects.equals(corporateSurcharge, charge.corporateSurcharge) &&
                Objects.equals(historic, charge.historic) &&
                Objects.equals(refundAvailabilityStatus, charge.refundAvailabilityStatus) &&
                Objects.equals(reference, charge.reference) &&
                Objects.equals(description, charge.description) &&
                Objects.equals(createdDate, charge.createdDate) &&
                Objects.equals(email, charge.email);
    }

    public String getExternalStatus() {
        return externalStatus;
    }

    public Long getGatewayAccountId() {
        return gatewayAccountId;
    }

    public String getPaymentGatewayName() {
        return paymentGatewayName;
    }

    public void setHistoric(boolean historic) {
        this.historic = historic;
    }
}
