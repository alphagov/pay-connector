package uk.gov.pay.connector.it.base;

import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.util.RandomIdGenerator;
import uk.gov.service.payments.commons.model.AuthorisationMode;

import java.time.Instant;

import static uk.gov.pay.connector.util.RandomGeneratorUtils.randomLong;
import static uk.gov.service.payments.commons.model.AuthorisationMode.WEB;

public record AddChargeParameters(long chargeId, String externalChargeId, ChargeStatus chargeStatus,
                                  ServicePaymentReference reference, Instant createdDate, String transactionId,
                                  String paymentProvider, AuthorisationMode authorisationMode) {


    public static final class Builder {
        private long chargeId = randomLong();
        private String externalChargeId = RandomIdGenerator.newId();
        private ChargeStatus chargeStatus;
        private ServicePaymentReference reference = ServicePaymentReference.of("ref");
        private Instant createdDate = Instant.now();
        private String transactionId = RandomIdGenerator.newId();
        private String paymentProvider = "sandbox";
        private AuthorisationMode authorisationMode = WEB;

        public static Builder anAddChargeParameters() {
            return new Builder();
        }

        public Builder withChargeId(long chargeId) {
            this.chargeId = chargeId;
            return this;
        }

        public Builder withExternalChargeId(String externalChargeId) {
            this.externalChargeId = externalChargeId;
            return this;
        }

        public Builder withChargeStatus(ChargeStatus chargeStatus) {
            this.chargeStatus = chargeStatus;
            return this;
        }

        public Builder withReference(ServicePaymentReference reference) {
            this.reference = reference;
            return this;
        }

        public Builder withCreatedDate(Instant createdDate) {
            this.createdDate = createdDate;
            return this;
        }

        public Builder withTransactionId(String transactionId) {
            this.transactionId = transactionId;
            return this;
        }

        public Builder withPaymentProvider(String paymentProvider) {
            this.paymentProvider = paymentProvider;
            return this;
        }

        public Builder withAuthorisationMode(AuthorisationMode authorisationMode) {
            this.authorisationMode = authorisationMode;
            return this;
        }

        public AddChargeParameters build() {
            return new AddChargeParameters(chargeId, externalChargeId, chargeStatus, reference, createdDate, transactionId, paymentProvider, authorisationMode);
        }
    }
}
