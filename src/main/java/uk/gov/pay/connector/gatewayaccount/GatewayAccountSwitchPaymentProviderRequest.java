package uk.gov.pay.connector.gatewayaccount;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GatewayAccountSwitchPaymentProviderRequest {

    public static final String USER_EXTERNAL_ID_FIELD = "user_external_id";
    public static final String GATEWAY_ACCOUNT_CREDENTIALS_ID_FIELD = "gateway_account_credential_id";

    private String userExternalId;
    private String gatewayAccountCredentialId;

    public GatewayAccountSwitchPaymentProviderRequest(@JsonProperty(USER_EXTERNAL_ID_FIELD) String userExternalId,
                                                      @JsonProperty(GATEWAY_ACCOUNT_CREDENTIALS_ID_FIELD) String gatewayAccountCredentialId) {
        this.userExternalId = userExternalId;
        this.gatewayAccountCredentialId = gatewayAccountCredentialId;
    }

    public String getUserExternalId() {
        return userExternalId;
    }

    public String getGatewayAccountCredentialId() {
        return gatewayAccountCredentialId;
    }
}
