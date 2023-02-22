package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.swagger.v3.oas.annotations.media.Schema;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.service.payments.commons.api.json.ApiResponseInstantSerializer;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GatewayAccountCredentials {

    @JsonProperty("gateway_account_credential_id")
    @Schema(example = "1")
    private Long id;

    @Schema(example = "787460d16d4a4d14b4c94787b8f427db")
    private String externalId;

    @Schema(example = "stripe")
    private String paymentProvider;

    @Schema(example = "{" +
            "  \"stripe_account_id\": \"accnt_id\"" +
            "  }")
    private Map<String, Object> credentials;

    @Schema(example = "ACTIVE")
    private GatewayAccountCredentialState state;

    @Schema(example = "vdwke0d16d4a4d14b4c94787b8f427d", description = "User external ID")
    private String lastUpdatedByUserExternalId;

    @JsonSerialize(using = ApiResponseInstantSerializer.class)
    @Schema(example = "2022-06-30T15:44:19.323Z")
    private Instant createdDate;

    @JsonSerialize(using = ApiResponseInstantSerializer.class)
    @Schema(example = "2022-06-28T16:40:56.869Z")
    private Instant activeStartDate;

    @JsonSerialize(using = ApiResponseInstantSerializer.class)
    @Schema(example = " ")
    private Instant activeEndDate;

    @Schema(example = "1")
    private Long gatewayAccountId;

    public GatewayAccountCredentials(GatewayAccountCredentialsEntity entity) {
        this.id = entity.getId();
        this.externalId = entity.getExternalId();
        this.paymentProvider = entity.getPaymentProvider();
        this.state = entity.getState();
        this.lastUpdatedByUserExternalId = entity.getLastUpdatedByUserExternalId();
        this.createdDate = entity.getCreatedDate();
        this.activeStartDate = entity.getActiveStartDate();
        this.activeEndDate = entity.getActiveEndDate();
        this.gatewayAccountId = entity.getGatewayAccountEntity().getId();

        this.credentials = removePasswords(entity.getCredentials());
    }

    private static Map<String, Object> removePasswords(Map<String, Object> credentials) {
        HashMap<String, Object> clonedCredentials = new HashMap<>(credentials);
        clonedCredentials.remove("password");
        credentials.forEach((key, value) -> {
            if (value instanceof Map<?, ?>) {
                var clonedNestedMap = new HashMap<>((Map<?,?>)value);
                clonedNestedMap.remove("password");
                clonedCredentials.put(key, clonedNestedMap);
            }
        });
        return clonedCredentials;
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

    public Map<String, Object> getCredentials() {
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
