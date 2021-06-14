package uk.gov.pay.connector.gatewayaccount;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GatewayAccountSwitchPaymentProviderRequest {

    public static final String USER_EXTERNAL_ID_FIELD = "user_external_id";
    public static final String GATEWAY_ACCOUNT_CREDENTIAL_EXTERNAL_ID = "gateway_account_credential_external_id";

    private String userExternalId;
    private String gatewayAccountCredentialExternalId;

    public GatewayAccountSwitchPaymentProviderRequest(@JsonProperty(USER_EXTERNAL_ID_FIELD) String userExternalId,
                                                      @JsonProperty(GATEWAY_ACCOUNT_CREDENTIAL_EXTERNAL_ID) String gatewayAccountCredentialExternalId) {
        this.userExternalId = userExternalId;
        this.gatewayAccountCredentialExternalId = gatewayAccountCredentialExternalId;
    }

    public String getUserExternalId() {
        return userExternalId;
    }

    public String getGACredentialExternalId() {
        return gatewayAccountCredentialExternalId;
    }
}
