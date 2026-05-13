package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.Optional;

public class AdyenGatewayAccountRequest extends GatewayAccountRequest {

    private final AdyenCredentials credentials;

    public AdyenGatewayAccountRequest(@JsonProperty("type") String providerAccountType,
                                      @JsonProperty("payment_provider") String paymentProvider,
                                      @JsonProperty("service_name") String serviceName,
                                      @JsonProperty("service_id") String serviceId,
                                      @JsonProperty("description") String description,
                                      @JsonProperty("analytics_id") String analyticsId,
                                      @JsonProperty("credentials") AdyenCredentials credentials,
                                      @JsonProperty("requires_3ds") boolean requires3ds,
                                      @JsonProperty("allow_apple_pay") boolean allowApplePay,
                                      @JsonProperty("allow_google_pay") boolean allowGooglePay,
                                      @JsonProperty("send_payer_email_to_gateway") boolean sendPayerEmailToGateway,
                                      @JsonProperty("send_payer_ip_address_to_gateway") boolean sendPayerIPAddressToGateway
    ) {
        super(providerAccountType,
                paymentProvider,
                serviceName,
                serviceId,
                description,
                analyticsId,
                requires3ds,
                allowApplePay,
                allowGooglePay,
                sendPayerEmailToGateway,
                sendPayerIPAddressToGateway);
        this.credentials = credentials;
    }

    @Override
    public Map<String, String> getCredentialsAsMap() {
        return Optional.ofNullable(credentials)
                .map(AdyenCredentials::toMap)
                .orElse(Map.of());
    }

    public static final class Builder {
        private AdyenCredentials credentials;
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

        public static Builder anAdyenAccountRequest() {
            return new AdyenGatewayAccountRequest.Builder();
        }

        public Builder withCredentials(AdyenCredentials credentials) {
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

        public AdyenGatewayAccountRequest build() {
            return new AdyenGatewayAccountRequest(providerAccountType, paymentProvider, serviceName, serviceId, description, analyticsId, credentials, requires3ds,
                    allowApplePay, allowGooglePay, sendPayerEmailToGateway, sendPayerIpAddressToGateway);
        }
    }
}
