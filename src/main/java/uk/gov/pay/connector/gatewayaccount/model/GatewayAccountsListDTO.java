package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
public class GatewayAccountsListDTO {

    @JsonProperty("accounts")
    private final List<GatewayAccountResponse> gatewayAccountsListDTOList;

    private GatewayAccountsListDTO(List<GatewayAccountResponse> gatewayAccountsListDTOList) {
        this.gatewayAccountsListDTOList = gatewayAccountsListDTOList;
    }


    public static GatewayAccountsListDTO of(List<GatewayAccountResponse> gatewayAccountsListDTOList) {
        return new GatewayAccountsListDTO(gatewayAccountsListDTOList);
    }
}
