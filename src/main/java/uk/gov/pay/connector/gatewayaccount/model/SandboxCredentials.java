package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SandboxCredentials implements GatewayCredentials {

    @Override
    public boolean hasCredentials() {
        return true;
    }
}
