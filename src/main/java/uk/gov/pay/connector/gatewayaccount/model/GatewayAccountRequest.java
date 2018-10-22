package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.validation.ValidationMethod;
import uk.gov.pay.connector.gateway.PaymentGatewayName;

import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity.Type.TEST;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GatewayAccountRequest extends AbstractGatewayAccount{

    private String paymentProvider;
    
    @JsonCreator
    GatewayAccountRequest(@JsonProperty("type") String providerAccountType,
                          @JsonProperty("payment_provider") String paymentProvider,
                          @JsonProperty("service_name") String serviceName,
                          @JsonProperty("description") String description,
                          @JsonProperty("analytics_id") String analyticsId) {
        this.serviceName = serviceName;
        this.description = description;
        this.analyticsId = analyticsId;

        this.providerAccountType = (providerAccountType == null || providerAccountType.isEmpty()) ?
                TEST.toString() : providerAccountType;

        this.paymentProvider = (paymentProvider == null || paymentProvider.isEmpty()) ?
                PaymentGatewayName.SANDBOX.getName() : paymentProvider;

    }

    @JsonProperty("payment_provider")
    public String getPaymentProvider() {
        return paymentProvider;
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


}
