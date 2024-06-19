package uk.gov.pay.connector.gatewayaccount;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import javax.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GatewayAccountSwitchPaymentProviderRequest {

    @NotNull(message = "Field [user_external_id] cannot be null")
    @JsonProperty("user_external_id")
    @Schema(example = "vfrg4245bd0e7453c9b1b0d7e6999f11b", description = "User external ID switching payment service provider")
    private String userExternalId;
    
    @NotNull(message = "Field [gateway_account_credential_external_id] cannot be null")
    @JsonProperty("gateway_account_credential_external_id")
    @Schema(example = "dfokpo23ji0213ldsm0123ofsm213kdfg", description = "Gateway account credential external ID to switch to")
    private String gatewayAccountCredentialExternalId;

    public GatewayAccountSwitchPaymentProviderRequest(String userExternalId, String gatewayAccountCredentialExternalId) {
        this.userExternalId = userExternalId;
        this.gatewayAccountCredentialExternalId = gatewayAccountCredentialExternalId;
    }

    public GatewayAccountSwitchPaymentProviderRequest() {
    }

    public String getUserExternalId() {
        return userExternalId;
    }

    public String getGatewayAccountCredentialExternalId() {
        return gatewayAccountCredentialExternalId;
    }
}
