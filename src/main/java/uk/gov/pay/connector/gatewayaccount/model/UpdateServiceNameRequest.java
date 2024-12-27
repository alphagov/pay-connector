package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import org.hibernate.validator.constraints.Length;

import jakarta.validation.constraints.NotNull;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateServiceNameRequest {
    
    @NotNull(message = "Field [service_name] cannot be null")
    @Length(max = 50, min = 1, message = "Field [service_name] can have a size between 1 and 50")
    private String serviceName;

    public String getServiceName() {
        return serviceName;
    }
}
