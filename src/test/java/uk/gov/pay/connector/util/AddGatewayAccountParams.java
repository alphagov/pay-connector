package uk.gov.pay.connector.util;

import uk.gov.pay.connector.gatewayaccount.model.EmailCollectionMode;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.util.Map;

import static uk.gov.pay.connector.gatewayaccount.model.EmailCollectionMode.MANDATORY;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity.Type.TEST;

public class AddGatewayAccountParams {
    private String accountId;
    private String paymentGateway;
    private Map<String, String> credentials;
    private String serviceName;
    private GatewayAccountEntity.Type providerUrlType;
    private String description;
    private String analyticsId;
    private EmailCollectionMode emailCollectionMode;
    private long corporateCreditCardSurchargeAmount;
    private long corporateDebitCardSurchargeAmount;
    private long corporatePrepaidCreditCardSurchargeAmount;
    private long corporatePrepaidDebitCardSurchargeAmount;
    private int integrationVersion3ds;

    public int getIntegrationVersion3ds() {
        return integrationVersion3ds;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getPaymentGateway() {
        return paymentGateway;
    }

    public Map<String, String> getCredentials() {
        return credentials;
    }

    public String getServiceName() {
        return serviceName;
    }

    public GatewayAccountEntity.Type getProviderUrlType() {
        return providerUrlType;
    }

    public String getDescription() {
        return description;
    }

    public String getAnalyticsId() {
        return analyticsId;
    }

    public EmailCollectionMode getEmailCollectionMode() {
        return emailCollectionMode;
    }

    public long getCorporateCreditCardSurchargeAmount() {
        return corporateCreditCardSurchargeAmount;
    }

    public long getCorporateDebitCardSurchargeAmount() {
        return corporateDebitCardSurchargeAmount;
    }

    public long getCorporatePrepaidCreditCardSurchargeAmount() {
        return corporatePrepaidCreditCardSurchargeAmount;
    }

    public long getCorporatePrepaidDebitCardSurchargeAmount() {
        return corporatePrepaidDebitCardSurchargeAmount;
    }

    public static final class AddGatewayAccountParamsBuilder {
        private String accountId;
        private String paymentGateway;
        private Map<String, String> credentials;
        private String serviceName;
        private GatewayAccountEntity.Type providerUrlType = TEST;
        private String description;
        private String analyticsId;
        private EmailCollectionMode emailCollectionMode = MANDATORY;
        private long corporateCreditCardSurchargeAmount;
        private long corporateDebitCardSurchargeAmount;
        private long corporatePrepaidCreditCardSurchargeAmount;
        private long corporatePrepaidDebitCardSurchargeAmount;
        private int integrationVersion3ds = 2;

        private AddGatewayAccountParamsBuilder() {
        }

        public static AddGatewayAccountParamsBuilder anAddGatewayAccountParams() {
            return new AddGatewayAccountParamsBuilder();
        }

        public AddGatewayAccountParamsBuilder withAccountId(String accountId) {
            this.accountId = accountId;
            return this;
        }

        public AddGatewayAccountParamsBuilder withPaymentGateway(String paymentGateway) {
            this.paymentGateway = paymentGateway;
            return this;
        }

        public AddGatewayAccountParamsBuilder withCredentials(Map<String, String> credentials) {
            this.credentials = credentials;
            return this;
        }

        public AddGatewayAccountParamsBuilder withServiceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        public AddGatewayAccountParamsBuilder withProviderUrlType(GatewayAccountEntity.Type providerUrlType) {
            this.providerUrlType = providerUrlType;
            return this;
        }

        public AddGatewayAccountParamsBuilder withDescription(String description) {
            this.description = description;
            return this;
        }

        public AddGatewayAccountParamsBuilder withAnalyticsId(String analyticsId) {
            this.analyticsId = analyticsId;
            return this;
        }

        public AddGatewayAccountParamsBuilder withEmailCollectionMode(EmailCollectionMode emailCollectionMode) {
            this.emailCollectionMode = emailCollectionMode;
            return this;
        }

        public AddGatewayAccountParamsBuilder withCorporateCreditCardSurchargeAmount(long corporateCreditCardSurchargeAmount) {
            this.corporateCreditCardSurchargeAmount = corporateCreditCardSurchargeAmount;
            return this;
        }

        public AddGatewayAccountParamsBuilder withCorporateDebitCardSurchargeAmount(long corporateDebitCardSurchargeAmount) {
            this.corporateDebitCardSurchargeAmount = corporateDebitCardSurchargeAmount;
            return this;
        }

        public AddGatewayAccountParamsBuilder withCorporatePrepaidCreditCardSurchargeAmount(long corporatePrepaidCreditCardSurchargeAmount) {
            this.corporatePrepaidCreditCardSurchargeAmount = corporatePrepaidCreditCardSurchargeAmount;
            return this;
        }

        public AddGatewayAccountParamsBuilder withCorporatePrepaidDebitCardSurchargeAmount(long corporatePrepaidDebitCardSurchargeAmount) {
            this.corporatePrepaidDebitCardSurchargeAmount = corporatePrepaidDebitCardSurchargeAmount;
            return this;
        }

        public AddGatewayAccountParams build() {
            AddGatewayAccountParams addGatewayAccountParams = new AddGatewayAccountParams();
            addGatewayAccountParams.accountId = this.accountId;
            addGatewayAccountParams.paymentGateway = this.paymentGateway;
            addGatewayAccountParams.corporatePrepaidDebitCardSurchargeAmount = this.corporatePrepaidDebitCardSurchargeAmount;
            addGatewayAccountParams.analyticsId = this.analyticsId;
            addGatewayAccountParams.corporatePrepaidCreditCardSurchargeAmount = this.corporatePrepaidCreditCardSurchargeAmount;
            addGatewayAccountParams.providerUrlType = this.providerUrlType;
            addGatewayAccountParams.credentials = this.credentials;
            addGatewayAccountParams.description = this.description;
            addGatewayAccountParams.serviceName = this.serviceName;
            addGatewayAccountParams.corporateCreditCardSurchargeAmount = this.corporateCreditCardSurchargeAmount;
            addGatewayAccountParams.emailCollectionMode = this.emailCollectionMode;
            addGatewayAccountParams.corporateDebitCardSurchargeAmount = this.corporateDebitCardSurchargeAmount;
            addGatewayAccountParams.integrationVersion3ds = this.integrationVersion3ds;
            return addGatewayAccountParams;
        }

        public AddGatewayAccountParamsBuilder withIntegrationVersion3ds(int integrationVersion3ds) {
            this.integrationVersion3ds = integrationVersion3ds;
            return this;
        }
    }
}
