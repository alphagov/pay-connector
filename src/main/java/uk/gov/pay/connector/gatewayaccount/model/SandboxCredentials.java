package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SandboxCredentials implements GatewayCredentials {

    @Override
    public boolean hasCredentials() {
        // Sandbox accounts don't have any credentials. Return true to indicate credentials are fully initialised.
        return true;
    }
}
