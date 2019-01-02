package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.dropwizard.validation.ValidationMethod;
import uk.gov.pay.connector.gateway.PaymentGatewayName;

import java.util.Map;

import static com.google.common.collect.Maps.newHashMap;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity.Type.TEST;

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

    private String providerAccountType;

    private String serviceName;

    private String description;

    private String analyticsId;

    private String paymentProvider;
    
    private boolean requires3ds;
    
    public GatewayAccountRequest(@JsonProperty("type") String providerAccountType,
                                 @JsonProperty("payment_provider") String paymentProvider,
                                 @JsonProperty("service_name") String serviceName,
                                 @JsonProperty("description") String description,
                                 @JsonProperty("analytics_id") String analyticsId,
                                 @JsonProperty("requires_3ds") boolean requires3ds
    ) {
        this.serviceName = serviceName;
        this.description = description;
        this.analyticsId = analyticsId;
        this.requires3ds = requires3ds;

        this.providerAccountType = (providerAccountType == null || providerAccountType.isEmpty()) ?
                TEST.toString() : providerAccountType;

        this.paymentProvider = (paymentProvider == null || paymentProvider.isEmpty()) ?
                PaymentGatewayName.SANDBOX.getName() : paymentProvider;

    }

    @ValidationMethod(message = "Unsupported payment provider account type, should be one of (test, live)")
    @JsonIgnore
    public boolean isValidProviderAccountType() {
        try {
            GatewayAccountEntity.Type.fromString(providerAccountType);
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

    public String getDescription() {
        return description;
    }

    public String getAnalyticsId() {
        return analyticsId;
    }

    public Map<String, String> getCredentialsAsMap() {
        return newHashMap();
    }

    public boolean getRequires3ds() {
        return requires3ds;
    }
}
