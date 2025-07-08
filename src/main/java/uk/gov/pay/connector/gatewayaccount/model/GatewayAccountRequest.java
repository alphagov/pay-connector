package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.dropwizard.validation.ValidationMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import uk.gov.pay.connector.gateway.PaymentGatewayName;

import jakarta.validation.constraints.NotBlank;
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
    @NotBlank(message = "Field [service_id] cannot be blank or missing")
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
    
    @JsonIgnore
    private boolean sendPayerEmailToGateway;
    
    @JsonIgnore
    private boolean sendPayerIpAddressToGateway;

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
                                 @JsonProperty("allow_google_pay") @Schema(description = "Set to 'true' to enable Google Pay for this account") boolean allowGooglePay,
                                 @JsonProperty("send_payer_email_to_gateway") @Schema(description = "Set to 'true' to enable send payer's email for this account") boolean sendPayerEmailToGateway,
                                 @JsonProperty("send_payer_ip_address_to_gateway") @Schema(description = "Set to 'true' to enable send payer's IP address for this account") boolean sendPayerIpAddressToGateway
    ) {
        this.serviceName = serviceName;
        this.serviceId = serviceId;
        this.description = description;
        this.analyticsId = analyticsId;
        this.requires3ds = requires3ds;
        this.allowApplePay = allowApplePay;
        this.allowGooglePay = allowGooglePay;
        this.sendPayerEmailToGateway = sendPayerEmailToGateway;
        this.sendPayerIpAddressToGateway = sendPayerIpAddressToGateway;

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

    public boolean isSendPayerEmailToGateway() {
        return sendPayerEmailToGateway;
    }

    public boolean isSendPayerIpAddressToGateway() {
        return sendPayerIpAddressToGateway;
    }
}
