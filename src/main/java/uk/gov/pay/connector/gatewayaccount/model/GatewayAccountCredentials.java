package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.service.payments.commons.api.json.ApiResponseInstantSerializer;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GatewayAccountCredentials {

    @JsonProperty("gateway_account_credential_id")
    private Long id;
    
    private String externalId;
    
    private String paymentProvider;
            
    private Map<String, String> credentials;
    
    private GatewayAccountCredentialState state;
    
    private String lastUpdatedByUserExternalId;

    @JsonSerialize(using = ApiResponseInstantSerializer.class)
    private Instant createdDate;

    @JsonSerialize(using = ApiResponseInstantSerializer.class)
    private Instant activeStartDate;

    @JsonSerialize(using = ApiResponseInstantSerializer.class)
    private Instant activeEndDate;
    
    private Long gatewayAccountId;

    public GatewayAccountCredentials(GatewayAccountCredentialsEntity entity) {
        this.id = entity.getId();
        this.externalId = entity.getExternalId();
        this.paymentProvider = entity.getPaymentProvider();
        this.state = entity.getState();
        this.lastUpdatedByUserExternalId = entity.getLastUpdatedByUserExternalId();
        this.activeStartDate = entity.getActiveStartDate();
        this.activeEndDate = entity.getActiveEndDate();
        this.gatewayAccountId = entity.getGatewayAccountEntity().getId();

        var clonedCredentials = new HashMap<>(entity.getCredentials());
        clonedCredentials.remove("password");
        this.credentials = clonedCredentials;
    }

    public Long getId() {
        return id;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getPaymentProvider() {
        return paymentProvider;
    }

    public Map<String, String> getCredentials() {
        return credentials;
    }

    public GatewayAccountCredentialState getState() {
        return state;
    }

    public String getLastUpdatedByUserExternalId() {
        return lastUpdatedByUserExternalId;
    }

    public Instant getCreatedDate() {
        return createdDate;
    }

    public Instant getActiveStartDate() {
        return activeStartDate;
    }

    public Instant getActiveEndDate() {
        return activeEndDate;
    }

    public Long getGatewayAccountId() {
        return gatewayAccountId;
    }
}
