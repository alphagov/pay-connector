package uk.gov.pay.connector.charge.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.ToStringBuilder;
import uk.gov.pay.connector.agreement.model.AgreementResponse;
import uk.gov.pay.connector.charge.model.builder.AbstractChargeResponseBuilder;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.common.model.api.ExternalChargeState;
import uk.gov.pay.connector.common.model.api.ExternalTransactionState;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

public class FrontendChargeResponse extends ChargeResponse {
    public static class FrontendChargeResponseBuilder extends AbstractChargeResponseBuilder<FrontendChargeResponseBuilder, FrontendChargeResponse> {
        private String status;
        private GatewayAccountEntity gatewayAccount;
        private AgreementResponse agreement;
        private boolean savePaymentInstrumentToAgreement;

        public FrontendChargeResponseBuilder withStatus(String status) {
            this.status = status;
            ExternalChargeState externalChargeState = ChargeStatus.fromString(status).toExternal();
            super.withState(new ExternalTransactionState(externalChargeState.getStatus(), externalChargeState.isFinished(), externalChargeState.getCode(), externalChargeState.getMessage()));
            return this;
        }

        public FrontendChargeResponseBuilder withGatewayAccount(GatewayAccountEntity gatewayAccountEntity) {
            this.gatewayAccount = gatewayAccountEntity;
            return this;
        }

        public FrontendChargeResponseBuilder withAgreement(AgreementResponse agreement) {
            this.agreement = agreement;
            return this;
        }

        public FrontendChargeResponseBuilder withSavePaymentInstrumentToAgreement(boolean savePaymentInstrumentToAgreement) {
            this.savePaymentInstrumentToAgreement = savePaymentInstrumentToAgreement;
            return this;
        }

        @Override
        protected FrontendChargeResponseBuilder thisObject() {
            return this;
        }

        @Override
        public FrontendChargeResponse build() {
            return new FrontendChargeResponse(this);
        }
    }

    public static FrontendChargeResponseBuilder aFrontendChargeResponse() {
        return new FrontendChargeResponseBuilder();
    }

    @JsonProperty
    private String status;

    @JsonProperty(value = "gateway_account")
    private GatewayAccountEntity gatewayAccount;

    @JsonProperty("save_payment_instrument_to_agreement")
    private boolean savePaymentInstrumentToAgreement;

    @JsonProperty
    private AgreementResponse agreement;

    private FrontendChargeResponse(FrontendChargeResponseBuilder builder) {
        super(builder);
        this.status = builder.status;
        this.gatewayAccount = builder.gatewayAccount;
        this.savePaymentInstrumentToAgreement = builder.savePaymentInstrumentToAgreement;
        this.agreement = builder.agreement;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        FrontendChargeResponse that = (FrontendChargeResponse) o;

        if (status != null ? !status.equals(that.status) : that.status != null) return false;
        if (cardDetails != null ? !cardDetails.equals(that.cardDetails) : that.cardDetails != null) return false;
        if (agreement != null ? !agreement.equals(that.agreement) : that.agreement == null) return false;
        return gatewayAccount != null ? gatewayAccount.equals(that.gatewayAccount) : that.gatewayAccount == null;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (status != null ? status.hashCode() : 0);
        result = 31 * result + (cardDetails != null ? cardDetails.hashCode() : 0);
        result = 31 * result + (gatewayAccount != null ? gatewayAccount.hashCode() : 0);
        result = 31 * result + (agreement != null ? agreement.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).appendSuper(super.toString()).reflectionToString(this);
    }
}
