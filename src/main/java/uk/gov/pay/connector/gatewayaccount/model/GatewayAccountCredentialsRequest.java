package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.validation.ValidationMethod;

import javax.validation.constraints.NotNull;

import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GatewayAccountCredentialsRequest {
    private String paymentProvider;
    private Map<String, String> credentials;
    
    public GatewayAccountCredentialsRequest(@JsonProperty("payment_provider") String paymentProvider, @JsonProperty("credentials") Map<String, String> credentials) {
        this.credentials = credentials;
        this.paymentProvider = paymentProvider;

    }

//    @ValidationMethod(message = "Unsupported payment provider value.")
//    @JsonIgnore
//    public boolean isValidPaymentProvider() {
//        return paymentProvider.equals(WORLDPAY.getName()) ||
//                paymentProvider.equals(STRIPE.getName());
//    }

    public String getPaymentProvider() {
        return paymentProvider;
    }

    public Map<String, String> getCredentialsAsMap() {
        return credentials;
    }
}
