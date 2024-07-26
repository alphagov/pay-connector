package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.dropwizard.validation.ValidationMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import uk.gov.pay.connector.gateway.PaymentGatewayName;

import javax.validation.constraints.NotBlank;
import java.util.Map;

import static com.google.common.collect.Maps.newHashMap;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "payment_provider",
        visible = true,
        defaultImpl = GatewayAccountRequest.class)
@JsonSubTypes({
        @JsonSubTypes.Type(value = StripeGatewayAccountRequest.class, name = "stripe")
})
@JsonIgnoreProperties(ignoreUnknown = true)
public class GatewayAccountRequest {

    @JsonIgnore
    @NotBlank
    private String providerAccountType;

    @JsonIgnore
    private String serviceName;

    @JsonIgnore
    private final String serviceId;

    @JsonIgnore
    private String description;

    @JsonIgnore
    private String analyticsId;

    @JsonIgnore
    private String paymentProvider;

    @JsonIgnore
    private boolean requires3ds;
    
    @JsonIgnore
    private boolean allowApplePay;
    
    @JsonIgnore
    private boolean allowGooglePay;

    public GatewayAccountRequest(@JsonProperty("type") @Schema(example = "live", description = "Account type for this provider (test/live)", defaultValue = "test") String providerAccountType,
                                 @JsonProperty("payment_provider") @Schema(example = "stripe", description = "The payment provider for which this account is created", defaultValue = "sandbox")
                                 String paymentProvider,
                                 @JsonProperty("service_name") @Schema(example = "service name") String serviceName,
                                 @JsonProperty("service_id") @Schema(example = "service-external-id") String serviceId,
                                 @JsonProperty("description") @Schema(description = "Some useful non-ambiguous description about the gateway account", example = "account for some gov org") String description,
                                 @JsonProperty("analytics_id") @Schema(description = "Google Analytics (GA) unique ID for the GOV.UK Pay platform", example = "analytics-id")
                                 String analyticsId,
                                 @JsonProperty("requires_3ds") @Schema(description = "Set to 'true' to enable 3DS for this account") boolean requires3ds,
                                 @JsonProperty("allow_apple_pay") @Schema(description = "Set to 'true' to enable Apple Pay for this account") boolean allowApplePay,
                                 @JsonProperty("allow_google_pay") @Schema(description = "Set to 'true' to enable Google Pay for this account") boolean allowGooglePay
    ) {
        this.serviceName = serviceName;
        this.serviceId = serviceId;
        this.description = description;
        this.analyticsId = analyticsId;
        this.requires3ds = requires3ds;
        this.allowApplePay = allowApplePay;
        this.allowGooglePay = allowGooglePay;

        this.providerAccountType = (providerAccountType == null || providerAccountType.isEmpty()) ?
                TEST.toString() : providerAccountType;

        this.paymentProvider = (paymentProvider == null || paymentProvider.isEmpty()) ?
                PaymentGatewayName.SANDBOX.getName() : paymentProvider;
    }

    @ValidationMethod(message = "Unsupported payment provider account type, should be one of (test, live)")
    @JsonIgnore
    public boolean isValidProviderAccountType() {
        try {
            GatewayAccountType.fromString(providerAccountType);
            return true;
        } catch (IllegalArgumentException iae) {
            return false;
        }
    }

    @ValidationMethod(message = "Unsupported payment provider value.")
    @JsonIgnore
    public boolean isValidPaymentProvider() {
        return PaymentGatewayName.isValidPaymentGateway(paymentProvider);
    }

    public String getPaymentProvider() {
        return paymentProvider;
    }

    public String getProviderAccountType() {
        return providerAccountType;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getServiceId() {
        return serviceId;
    }

    public String getDescription() {
        return description;
    }

    public String getAnalyticsId() {
        return analyticsId;
    }

    @JsonIgnore
    public Map<String, String> getCredentialsAsMap() {
        return newHashMap();
    }

    public boolean isRequires3ds() {
        return requires3ds;
    }

    public boolean isAllowApplePay() {
        return allowApplePay;
    }

    public boolean isAllowGooglePay() {
        return allowGooglePay;
    }
    
    public static final class GatewayAccountRequestBuilder {
        private @NotBlank String providerAccountType;
        private String serviceName;
        private String serviceId;
        private String description;
        private String analyticsId;
        private String paymentProvider;
        private boolean requires3ds;
        private boolean allowApplePay;
        private boolean allowGooglePay;

        private GatewayAccountRequestBuilder() {
        }

        public static GatewayAccountRequestBuilder builder() {
            return new GatewayAccountRequestBuilder();
        }

        public GatewayAccountRequestBuilder withProviderAccountType(String providerAccountType) {
            this.providerAccountType = providerAccountType;
            return this;
        }

        public GatewayAccountRequestBuilder withServiceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        public GatewayAccountRequestBuilder withServiceId(String serviceId) {
            this.serviceId = serviceId;
            return this;
        }

        public GatewayAccountRequestBuilder withDescription(String description) {
            this.description = description;
            return this;
        }

        public GatewayAccountRequestBuilder withAnalyticsId(String analyticsId) {
            this.analyticsId = analyticsId;
            return this;
        }

        public GatewayAccountRequestBuilder withPaymentProvider(String paymentProvider) {
            this.paymentProvider = paymentProvider;
            return this;
        }

        public GatewayAccountRequestBuilder withRequires3ds(boolean requires3ds) {
            this.requires3ds = requires3ds;
            return this;
        }

        public GatewayAccountRequestBuilder withAllowApplePay(boolean allowApplePay) {
            this.allowApplePay = allowApplePay;
            return this;
        }

        public GatewayAccountRequestBuilder withAllowGooglePay(boolean allowGooglePay) {
            this.allowGooglePay = allowGooglePay;
            return this;
        }

        public GatewayAccountRequest build() {
            return new GatewayAccountRequest(providerAccountType, paymentProvider, serviceName, serviceId, description, analyticsId, requires3ds, allowApplePay, allowGooglePay);
        }
    }
}
