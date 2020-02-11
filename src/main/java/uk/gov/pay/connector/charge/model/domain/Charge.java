package uk.gov.pay.connector.charge.model.domain;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;

public class Charge {

    private String externalId;
    private Long amount;
    private String status;
    private String gatewayTransactionId;
    private Long corporateSurcharge;
    private String refundAvailabilityStatus;
    private String reference;
    private String description;
    private ZonedDateTime createdDate;
    private String email;
    private boolean historic;

    public Charge(String externalId, Long amount, String status, String gatewayTransactionId,
                  Long corporateSurcharge, String refundAvailabilityStatus, String reference,
                  String description, ZonedDateTime createdDate, String email, boolean historic) {
        this.externalId = externalId;
        this.amount = amount;
        this.status = status;
        this.gatewayTransactionId = gatewayTransactionId;
        this.corporateSurcharge = corporateSurcharge;
        this.refundAvailabilityStatus = refundAvailabilityStatus;
        this.reference = reference;
        this.description = description;
        this.createdDate = createdDate;
        this.email = email;
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
                chargeEntity.getReference().toString(),
                chargeEntity.getDescription(),
                chargeEntity.getCreatedDate(),
                chargeEntity.getEmail(),
                false);
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
}
