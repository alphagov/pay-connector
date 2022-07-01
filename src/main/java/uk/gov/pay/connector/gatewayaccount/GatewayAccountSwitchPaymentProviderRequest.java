package uk.gov.pay.connector.gatewayaccount;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GatewayAccountSwitchPaymentProviderRequest {

    public static final String USER_EXTERNAL_ID_FIELD = "user_external_id";
    public static final String GATEWAY_ACCOUNT_CREDENTIAL_EXTERNAL_ID = "gateway_account_credential_external_id";

    @JsonIgnore
    private String userExternalId;
    @JsonIgnore
    private String gatewayAccountCredentialExternalId;

    public GatewayAccountSwitchPaymentProviderRequest(@Schema(example = "vfrg4245bd0e7453c9b1b0d7e6999f11b", description = "User external ID switching payment service provider")
                                                      @JsonProperty(USER_EXTERNAL_ID_FIELD) String userExternalId,
                                                      @Schema(example = "dfokpo23ji0213ldsm0123ofsm213kdfg", description = "Gateway account credential external ID to switch to")
                                                      @JsonProperty(GATEWAY_ACCOUNT_CREDENTIAL_EXTERNAL_ID) String gatewayAccountCredentialExternalId) {
        this.userExternalId = userExternalId;
        this.gatewayAccountCredentialExternalId = gatewayAccountCredentialExternalId;
    }

    public String getUserExternalId() {
        return userExternalId;
    }

    @JsonIgnore
    public String getGACredentialExternalId() {
        return gatewayAccountCredentialExternalId;
    }
}
