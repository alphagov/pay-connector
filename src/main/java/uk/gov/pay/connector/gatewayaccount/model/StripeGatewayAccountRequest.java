package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StripeGatewayAccountRequest extends GatewayAccountRequest {

    private final StripeCredentials credentials;
    
    public StripeGatewayAccountRequest(@JsonProperty("type") String providerAccountType,
                                       @JsonProperty("payment_provider") String paymentProvider,
                                       @JsonProperty("service_name") String serviceName,
                                       @JsonProperty("service_id") String serviceId,
                                       @JsonProperty("description") String description,
                                       @JsonProperty("analytics_id") String analyticsId,
                                       @JsonProperty("credentials") StripeCredentials credentials,
                                       @JsonProperty("requires_3ds") boolean requires3ds,
                                       @JsonProperty("allow_apple_pay") boolean allowApplePay,
                                       @JsonProperty("allow_google_pay") boolean allowGooglePay,
                                       @JsonProperty("send_payer_email_to_gateway") boolean sendPayerEmailToGateway,
                                       @JsonProperty("send_payer_ip_address_to_gateway") boolean sendPayerIPAddressToGateway
    ) {
        super(providerAccountType, paymentProvider, serviceName, serviceId, description, analyticsId, requires3ds, allowApplePay, 
                allowGooglePay, sendPayerEmailToGateway, sendPayerIPAddressToGateway);
        this.credentials = credentials;
    }

    @Override
    public Map<String, String> getCredentialsAsMap() {
        return Optional.ofNullable(credentials).map(StripeCredentials::toMap).orElse(Map.of());
    }
    
    public static final class Builder {
        private StripeCredentials credentials;
        private String providerAccountType;
        private String serviceName;
        private String serviceId;
        private String description;
        private String analyticsId;
        private String paymentProvider;
        private boolean requires3ds;
        private boolean allowApplePay;
        private boolean allowGooglePay;
        private boolean sendPayerEmailToGateway;
        private boolean sendPayerIpAddressToGateway;

        private Builder() {
        }

        public static Builder aStripeGatewayAccountRequest() {
            return new Builder();
        }

        public Builder withCredentials(StripeCredentials credentials) {
            this.credentials = credentials;
            return this;
        }

        public Builder withProviderAccountType(String providerAccountType) {
            this.providerAccountType = providerAccountType;
            return this;
        }

        public Builder withServiceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        public Builder withServiceId(String serviceId) {
            this.serviceId = serviceId;
            return this;
        }

        public Builder withDescription(String description) {
            this.description = description;
            return this;
        }

        public Builder withAnalyticsId(String analyticsId) {
            this.analyticsId = analyticsId;
            return this;
        }

        public Builder withPaymentProvider(String paymentProvider) {
            this.paymentProvider = paymentProvider;
            return this;
        }

        public Builder withRequires3ds(boolean requires3ds) {
            this.requires3ds = requires3ds;
            return this;
        }

        public Builder withAllowApplePay(boolean allowApplePay) {
            this.allowApplePay = allowApplePay;
            return this;
        }

        public Builder withAllowGooglePay(boolean allowGooglePay) {
            this.allowGooglePay = allowGooglePay;
            return this;
        }
        
        public Builder withSendPayerEmailToGateway(boolean sendPayerEmailToGateway) {
            this.sendPayerEmailToGateway = sendPayerEmailToGateway;
            return this;
        }
        
        public Builder withSendPayerIpAddressToGateway(boolean sendPayerIpAddressToGateway) {
            this.sendPayerIpAddressToGateway = sendPayerIpAddressToGateway;
            return this;
        }

        public StripeGatewayAccountRequest build() {
            return new StripeGatewayAccountRequest(providerAccountType, paymentProvider, serviceName, serviceId, description, analyticsId, credentials, requires3ds, 
                    allowApplePay, allowGooglePay, sendPayerEmailToGateway, sendPayerIpAddressToGateway);
        }
    }
}
