package uk.gov.pay.connector.service;


import uk.gov.pay.connector.model.OrderRequestType;
import uk.gov.pay.connector.model.domain.AuthCardDetails;
import uk.gov.pay.connector.util.templates.PayloadBuilder;

import javax.ws.rs.core.MediaType;

import static org.apache.commons.lang3.StringUtils.defaultString;

public abstract class OrderRequestBuilder {
    public static class TemplateData {
        private String transactionId;
        private String merchantCode;
        private String description;
        private AuthCardDetails authCardDetails;
        private String amount;
        private String paymentPlatformReference;

        public String getTransactionId() {
            return transactionId;
        }

        public void setTransactionId(String transactionId) {
            this.transactionId = transactionId;
        }

        public String getMerchantCode() {
            return merchantCode;
        }

        public void setMerchantCode(String merchantCode) {
            this.merchantCode = merchantCode;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public AuthCardDetails getAuthCardDetails() {
            return authCardDetails;
        }

        public void setAuthCardDetails(AuthCardDetails authCardDetails) {
            this.authCardDetails = authCardDetails;
        }

        public String getAmount() {
            return amount;
        }

        public void setAmount(String amount) {
            this.amount = amount;
        }

        public String getPaymentPlatformReference() {
            return paymentPlatformReference;
        }

        public void setPaymentPlatformReference(String paymentPlatformReference) {
            this.paymentPlatformReference = paymentPlatformReference;
        }
    }

    private final TemplateData templateData;

    private PayloadBuilder payloadBuilder;
    private OrderRequestType orderRequestType;
    private String providerSessionId;

    public OrderRequestBuilder(TemplateData templateData, PayloadBuilder payloadBuilder, OrderRequestType orderRequestType) {
        this.templateData = templateData;
        this.payloadBuilder = payloadBuilder;
        this.orderRequestType = orderRequestType;
    }

    public abstract MediaType getMediaType();

    public OrderRequestBuilder withTransactionId(String transactionId) {
        templateData.setTransactionId(defaultString(transactionId));
        return this;
    }

    public OrderRequestBuilder withMerchantCode(String merchantCode) {
        templateData.setMerchantCode(merchantCode);
        return this;
    }

    public OrderRequestBuilder withPaymentPlatformReference(String reference) {
        templateData.setPaymentPlatformReference(reference);
        return this;
    }


    public OrderRequestBuilder withDescription(String description) {
        templateData.setDescription(description);
        return this;
    }

    public OrderRequestBuilder withAuthorisationDetails(AuthCardDetails authCardDetails) {
        templateData.setAuthCardDetails(authCardDetails);
        return this;
    }

    public OrderRequestBuilder withAmount(String amount) {
        templateData.setAmount(amount);
        return this;
    }

    public OrderRequestBuilder withProviderSessionId(String providerSessionId) {
        this.providerSessionId = providerSessionId;
        return this;
    }

    public GatewayOrder build() {
        return new GatewayOrder(
                orderRequestType,
                payloadBuilder.buildWith(templateData), providerSessionId, getMediaType());
    }
}
