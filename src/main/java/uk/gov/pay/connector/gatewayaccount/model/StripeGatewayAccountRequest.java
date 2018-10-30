package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.Optional;

import static com.google.common.collect.Maps.newHashMap;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StripeGatewayAccountRequest extends GatewayAccountRequest {

    private Optional<StripeCredentials> credentials;
    
    public StripeGatewayAccountRequest(@JsonProperty("type") String providerAccountType,
                                       @JsonProperty("payment_provider") String paymentProvider,
                                       @JsonProperty("service_name") String serviceName,
                                       @JsonProperty("description") String description,
                                       @JsonProperty("analytics_id") String analyticsId,
                                       @JsonProperty("credentials") StripeCredentials credentials) {
        
        super(providerAccountType, paymentProvider, serviceName, description, analyticsId);
        this.credentials = Optional.ofNullable(credentials);
    }

    public Optional<StripeCredentials> getCredentials() {
        return credentials;
    }

    @Override
    public Map<String, String> getCredentialsAsMap() {
        return credentials.isPresent() ? credentials.get().toMap() : newHashMap();
    }
}
