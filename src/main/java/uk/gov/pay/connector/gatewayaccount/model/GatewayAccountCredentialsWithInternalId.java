package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;

public class GatewayAccountCredentialsWithInternalId extends GatewayAccountCredentials {
    @JsonProperty("gateway_account_credential_id")
    @Schema(example = "1")
    private Long id;
    public GatewayAccountCredentialsWithInternalId(GatewayAccountCredentialsEntity entity) {
        super(entity);
        this.id = entity.getId();
    }

    public Long getId() {
        return id;
    }
    
    public GatewayAccountCredentials stripInternalId() {
        return this;
    }
}
