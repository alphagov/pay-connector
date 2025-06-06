package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
public class GatewayAccountWithCredentialsResponse extends GatewayAccountResponse {

    @JsonProperty("notifySettings")
    @Schema(description = "An object containing the Notify credentials and configuration for sending custom branded emails")
    private final Map<String, String> notifySettings;
    
    @JsonProperty("gateway_account_credentials")
    @Schema(description = "Array of the credentials configured for this account")
    private final List<GatewayAccountCredentials> gatewayAccountCredentials;

    public GatewayAccountWithCredentialsResponse(GatewayAccountEntity gatewayAccountEntity) {
        super(gatewayAccountEntity);
        this.notifySettings = gatewayAccountEntity.getNotifySettings();
        this.gatewayAccountCredentials = gatewayAccountEntity.getGatewayAccountCredentials()
                .stream()
                .map(GatewayAccountCredentials::new)
                .collect(Collectors.toList());
    }

    public Map<String, String> getNotifySettings() {
        return notifySettings;
    }

    public List<GatewayAccountCredentials> getGatewayAccountCredentials() {
        return gatewayAccountCredentials;
    }
}
