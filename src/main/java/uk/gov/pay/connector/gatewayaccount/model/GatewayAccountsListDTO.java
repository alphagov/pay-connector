package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
public class GatewayAccountsListDTO {

    @JsonProperty("accounts")
    private final List<GatewayAccountResourceDTO> gatewayAccountsListDTOList;

    private GatewayAccountsListDTO(List<GatewayAccountResourceDTO> gatewayAccountsListDTOList) {
        this.gatewayAccountsListDTOList = gatewayAccountsListDTOList;
    }


    public static GatewayAccountsListDTO of(List<GatewayAccountResourceDTO> gatewayAccountsListDTOList) {
        return new GatewayAccountsListDTO(gatewayAccountsListDTOList);
    }
}
