package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Update3dsToggleRequest {
    
    @NotNull
    @JsonProperty(value = "toggle_3ds")
    boolean toggle3ds;

    public boolean isToggle3ds() {
        return toggle3ds;
    }
}
