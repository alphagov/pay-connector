package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.hibernate.validator.constraints.NotEmpty;

import java.util.Optional;

public class NewGatewayAccountRequest {
    
    private String providerAccountType;
    @JsonProperty("name")
    private String serviceName;

    NewGatewayAccountRequest(@JsonProperty("type") String accountType,
                             @JsonProperty("name") String serviceName) {
        this.providerAccountType = accountType;
        this.serviceName = serviceName;
    }

    public String getServiceName() {
        return serviceName;
    }

    public GatewayAccountEntity.Type getProviderAccountType() {
        return null;
    }
    
    /*
    
        try {
            type = GatewayAccountEntity.Type.fromString(accountType);
        } catch (IllegalArgumentException iae) {
            return badRequestResponse(format("Unsupported payment provider account type '%s', should be one of (test, live)", accountType));
        }
     */
}
