package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.validation.ValidationMethod;
import uk.gov.pay.connector.gateway.PaymentGatewayName;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static com.google.common.collect.Maps.newHashMap;
import static uk.gov.pay.connector.gatewayaccount.CredentialsMapper.getCredentialsMapperForPaymentProvider;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity.Type.TEST;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GatewayAccountRequest {

    private String providerAccountType;

    private String serviceName;

    private String description;

    private String analyticsId;

    private String paymentProvider;

    private Map credentials;
    
    public GatewayAccountRequest(@JsonProperty("type") String providerAccountType,
                                 @JsonProperty("payment_provider") String paymentProvider,
                                 @JsonProperty("service_name") String serviceName,
                                 @JsonProperty("description") String description,
                                 @JsonProperty("analytics_id") String analyticsId,
                                 @JsonProperty("credentials") Map<String, String> credentials) {
        this.serviceName = serviceName;
        this.description = description;
        this.analyticsId = analyticsId;

        Optional<Function<Map<String, String>, Map<String, String>>> credentialsMapper = getCredentialsMapperForPaymentProvider(paymentProvider);
        if (credentialsMapper.isPresent()) {
            this.credentials = credentialsMapper.get().apply(credentials);
        } else {
            this.credentials = newHashMap();
        }

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

    public Map getCredentials() {
        return credentials;
    }
}
