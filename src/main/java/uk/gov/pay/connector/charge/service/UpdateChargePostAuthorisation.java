package uk.gov.pay.connector.charge.service;

import uk.gov.pay.connector.charge.model.domain.Auth3dsRequiredEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.ProviderSessionIdentifier;
import uk.gov.pay.connector.paymentprocessor.model.Exemption3ds;

import java.util.Objects;

public class UpdateChargePostAuthorisation {

    private String chargeExternalId;
    private ChargeStatus status;
    private String transactionId;
    private Auth3dsRequiredEntity auth3dsRequiredDetails;
    private ProviderSessionIdentifier sessionIdentifier;
    private AuthCardDetails authCardDetails;
    private Exemption3ds exemption3ds;

    public String getChargeExternalId() {
        return chargeExternalId;
    }

    public ChargeStatus getStatus() {
        return status;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public Auth3dsRequiredEntity getAuth3dsRequiredDetails() {
        return auth3dsRequiredDetails;
    }

    public ProviderSessionIdentifier getSessionIdentifier() {
        return sessionIdentifier;
    }

    public AuthCardDetails getAuthCardDetails() {
        return authCardDetails;
    }

    public Exemption3ds getExemption3ds() {
        return exemption3ds;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UpdateChargePostAuthorisation that = (UpdateChargePostAuthorisation) o;
        return Objects.equals(chargeExternalId, that.chargeExternalId) &&
                status == that.status &&
                Objects.equals(transactionId, that.transactionId) &&
                Objects.equals(auth3dsRequiredDetails, that.auth3dsRequiredDetails) &&
                Objects.equals(sessionIdentifier, that.sessionIdentifier) &&
                Objects.equals(authCardDetails, that.authCardDetails) &&
                exemption3ds == that.exemption3ds;
    }

    @Override
    public int hashCode() {
        return Objects.hash(chargeExternalId, status, transactionId, auth3dsRequiredDetails, sessionIdentifier, authCardDetails, exemption3ds);
    }

    public static final class UpdateChargePostAuthorisationBuilder {
        private String chargeExternalId;
        private ChargeStatus status;
        private String transactionId;
        private Auth3dsRequiredEntity auth3dsRequiredDetails;
        private ProviderSessionIdentifier sessionIdentifier;
        private AuthCardDetails authCardDetails;
        private Exemption3ds exemption3ds;

        private UpdateChargePostAuthorisationBuilder() {
        }

        public static UpdateChargePostAuthorisationBuilder anUpdateChargePostAuthorisation() {
            return new UpdateChargePostAuthorisationBuilder();
        }

        public UpdateChargePostAuthorisationBuilder withChargeExternalId(String chargeExternalId) {
            this.chargeExternalId = chargeExternalId;
            return this;
        }

        public UpdateChargePostAuthorisationBuilder withStatus(ChargeStatus status) {
            this.status = status;
            return this;
        }

        public UpdateChargePostAuthorisationBuilder withTransactionId(String transactionId) {
            this.transactionId = transactionId;
            return this;
        }

        public UpdateChargePostAuthorisationBuilder withAuth3dsRequiredDetails(Auth3dsRequiredEntity auth3dsRequiredDetails) {
            this.auth3dsRequiredDetails = auth3dsRequiredDetails;
            return this;
        }

        public UpdateChargePostAuthorisationBuilder withSessionIdentifier(ProviderSessionIdentifier sessionIdentifier) {
            this.sessionIdentifier = sessionIdentifier;
            return this;
        }

        public UpdateChargePostAuthorisationBuilder withAuthCardDetails(AuthCardDetails authCardDetails) {
            this.authCardDetails = authCardDetails;
            return this;
        }

        public UpdateChargePostAuthorisationBuilder withExemption3ds(Exemption3ds exemption3ds) {
            this.exemption3ds = exemption3ds;
            return this;
        }

        public UpdateChargePostAuthorisation build() {
            Objects.requireNonNull(status);
            Objects.requireNonNull(authCardDetails);
            Objects.requireNonNull(chargeExternalId);
            
            UpdateChargePostAuthorisation updateChargePostAuthorisation = new UpdateChargePostAuthorisation();
            updateChargePostAuthorisation.transactionId = this.transactionId;
            updateChargePostAuthorisation.status = this.status;
            updateChargePostAuthorisation.sessionIdentifier = this.sessionIdentifier;
            updateChargePostAuthorisation.authCardDetails = this.authCardDetails;
            updateChargePostAuthorisation.auth3dsRequiredDetails = this.auth3dsRequiredDetails;
            updateChargePostAuthorisation.chargeExternalId = this.chargeExternalId;
            updateChargePostAuthorisation.exemption3ds = this.exemption3ds;
            return updateChargePostAuthorisation;
        }
    }
}
