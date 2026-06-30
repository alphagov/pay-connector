package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public class AdyenAccountSetupResponse {
    
    @JsonProperty("service_id")
    private String serviceId;

    @JsonProperty("credential_external_id")
    private String credentialExternalId;

    @JsonProperty("gateway_account_id")
    private long gatewayAccountId;

    @JsonProperty("tasks")
    private Map<String, Map<String, AdyenAccountSetupStatus>> tasks;
    
    public void setGatewayAccountId(long gatewayAccountId) {
        this.gatewayAccountId = gatewayAccountId;
    }

    public void setCredentialExternalId(String credentialExternalId) {
        this.credentialExternalId = credentialExternalId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }
    
    public void setTasks(Map<String, Map<String, AdyenAccountSetupStatus>> tasks) {
        this.tasks = tasks;
    }
}
