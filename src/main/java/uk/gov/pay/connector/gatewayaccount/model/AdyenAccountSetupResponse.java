package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public record AdyenAccountSetupResponse(
        @JsonProperty("service_id")
        String serviceId,
        
        @JsonProperty("credential_external_id")
        String credentialExternalId,
        
        @JsonProperty("gateway_account_id")
        long gatewayAccountId,
        
        @JsonProperty("tasks")
        Map<String, Map<String, AdyenAccountSetupStatus>> tasks
) {
}
