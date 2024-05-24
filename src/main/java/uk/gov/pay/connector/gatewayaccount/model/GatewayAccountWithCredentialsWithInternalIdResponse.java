package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.stream.Collectors;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
public class GatewayAccountWithCredentialsWithInternalIdResponse extends GatewayAccountWithCredentialsResponse {
    @JsonProperty("gateway_account_credentials")
    @Schema(description = "Array of the credentials configured for this account")
    private final List<GatewayAccountCredentialsWithInternalId> gatewayAccountCredentialsWithInternalIds;

    public GatewayAccountWithCredentialsWithInternalIdResponse(GatewayAccountEntity gatewayAccountEntity) {
        super(gatewayAccountEntity);
        this.gatewayAccountCredentialsWithInternalIds = gatewayAccountEntity.getGatewayAccountCredentials()
                .stream()
                .map(GatewayAccountCredentialsWithInternalId::new)
                .collect(Collectors.toList());
    }

    public List<GatewayAccountCredentialsWithInternalId> getGatewayAccountCredentialsWithInternalIds() {
        return gatewayAccountCredentialsWithInternalIds;
    }
}
