package uk.gov.pay.connector.charge.model.domain;

import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.service.payments.commons.model.AuthorisationMode;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public class Charge {

    private String externalId;
    private Long amount;
    private String status;
    private String externalStatus;
    private String gatewayTransactionId;
    private String credentialExternalId;
    private Long corporateSurcharge;
    private String refundAvailabilityStatus;
    private String reference;
    private String description;
    private Instant createdDate;
    private String email;
    private Long gatewayAccountId;
    private String paymentGatewayName;
    private boolean historic;
    private String serviceId;
    private boolean live;
    private Boolean disputed;
    private AuthorisationMode authorisationMode;
    private String agreementId;

    public Charge(String externalId, Long amount, String status, String externalStatus, String gatewayTransactionId,
                  String credentialExternalId, Long corporateSurcharge, String refundAvailabilityStatus, String reference,
                  String description, Instant createdDate, String email, Long gatewayAccountId, String paymentGatewayName,
                  boolean historic, String serviceId, boolean live, Boolean disputed, AuthorisationMode authorisationMode,
                  String agreementId) {
        this.externalId = externalId;
        this.amount = amount;
        this.status = status;
        this.externalStatus = externalStatus;
        this.gatewayTransactionId = gatewayTransactionId;
        this.credentialExternalId = credentialExternalId;
        this.corporateSurcharge = corporateSurcharge;
        this.refundAvailabilityStatus = refundAvailabilityStatus;
        this.reference = reference;
        this.description = description;
        this.createdDate = createdDate;
        this.email = email;
        this.gatewayAccountId = gatewayAccountId;
        this.paymentGatewayName = paymentGatewayName;
        this.historic = historic;
        this.serviceId = serviceId;
        this.live = live;
        this.disputed = disputed;
        this.authorisationMode = authorisationMode;
        this.agreementId = agreementId;
    }

    public static Charge from(ChargeEntity chargeEntity) {
        ChargeStatus chargeStatus = ChargeStatus.fromString(chargeEntity.getStatus());
        String credentialExternalId = null;

        if (chargeEntity.getGatewayAccountCredentialsEntity() != null) {
            credentialExternalId = chargeEntity.getGatewayAccountCredentialsEntity().getExternalId();
        }

        return new Charge(
                chargeEntity.getExternalId(),
                chargeEntity.getAmount(),
                chargeEntity.getStatus(),
                chargeStatus.toExternal().toString(),
                chargeEntity.getGatewayTransactionId(),
                credentialExternalId,
                chargeEntity.getCorporateSurcharge().orElse(null),
                null, 
                chargeEntity.getReference().toString(),
                chargeEntity.getDescription(),
                chargeEntity.getCreatedDate(),
                chargeEntity.getEmail(),
                chargeEntity.getGatewayAccount().getId(),
                chargeEntity.getPaymentGatewayName().getName(),
                false,
                chargeEntity.getServiceId(),
                chargeEntity.getGatewayAccount().isLive(), 
                null,
                chargeEntity.getAuthorisationMode(),
                chargeEntity.getAgreement().map(AgreementEntity::getExternalId).orElse(null));
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
                transaction.getCredentialExternalId(),
                transaction.getCorporateCardSurcharge(),
                externalRefundState,
                transaction.getReference(),
                transaction.getDescription(),
                Instant.parse(transaction.getCreatedDate()),
                transaction.getEmail(),
                Long.valueOf(transaction.getGatewayAccountId()),
                transaction.getPaymentProvider(),
                true,
                transaction.getServiceId(),
                transaction.getLive(),
                transaction.isDisputed(),
                transaction.getAuthorisationMode(),
                transaction.getAgreementId()
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

    public Instant getCreatedDate() {
        return createdDate;
    }

    public String getEmail() {
        return email;
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

    public Optional<String> getCredentialExternalId() {
        return Optional.ofNullable(credentialExternalId);
    }

    public void setCredentialExternalId(String credentialExternalId) {
        this.credentialExternalId = credentialExternalId;
    }
    
    public String getServiceId(){
        return serviceId;
    }

    public boolean isLive(){
        return live;
    }

    public Boolean getDisputed() {
        return disputed;
    }

    public AuthorisationMode getAuthorisationMode() {
        return authorisationMode;
    }

    public void setAuthorisationMode(AuthorisationMode authorisationMode) {
        this.authorisationMode = authorisationMode;
    }

    public Optional<String> getAgreementId() {
        return Optional.ofNullable(agreementId);
    }

    public void setAgreementId(String agreementId) {
        this.agreementId = agreementId;
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
                Objects.equals(credentialExternalId, charge.credentialExternalId) &&
                Objects.equals(corporateSurcharge, charge.corporateSurcharge) &&
                Objects.equals(historic, charge.historic) &&
                Objects.equals(refundAvailabilityStatus, charge.refundAvailabilityStatus) &&
                Objects.equals(reference, charge.reference) &&
                Objects.equals(description, charge.description) &&
                Objects.equals(createdDate, charge.createdDate) &&
                Objects.equals(email, charge.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(externalId, amount, status, externalStatus, gatewayTransactionId, credentialExternalId, corporateSurcharge,
                refundAvailabilityStatus, reference, description, createdDate, email, gatewayAccountId,
                paymentGatewayName, historic);
    }
}
