package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GatewayAccountCredentialsRequest {
    private String paymentProvider;
    private Map<String, String> credentials;
    public static final String PAYMENT_PROVIDER_FIELD_NAME = "payment_provider";

    public GatewayAccountCredentialsRequest(@JsonProperty(PAYMENT_PROVIDER_FIELD_NAME) String paymentProvider, @JsonProperty("credentials") Map<String, String> credentials) {
        this.credentials = credentials;
        this.paymentProvider = paymentProvider;
    }

    @JsonProperty(PAYMENT_PROVIDER_FIELD_NAME)
    public String getPaymentProvider() {
        return paymentProvider;
    }

    public Map<String, String> getCredentialsAsMap() {
        return credentials;
    }
}
