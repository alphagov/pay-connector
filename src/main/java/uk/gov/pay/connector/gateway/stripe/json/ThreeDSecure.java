package uk.gov.pay.connector.gateway.stripe.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ThreeDSecure {

    private String version;
    
    private Boolean authenticated;

    public String getVersion() {
        return version;
    }

    public Boolean getAuthenticated() {
        return authenticated;
    }
}
