package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EpdqCredentials implements GatewayCredentials {

    @JsonProperty("merchant_id")
    private String merchantId;
    
    @JsonProperty("username")
    private String username;
    
    @JsonProperty("password")
    private String password;
    
    @JsonProperty("sha_in_passphrase")
    private String shaInPassphrase;
    
    @JsonProperty("sha_out_passphrase")
    private String shaOutPassphrase;
    
    public EpdqCredentials() {
        // For Jackson
    }

    public String getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getShaInPassphrase() {
        return shaInPassphrase;
    }

    public void setShaInPassphrase(String shaInPassphrase) {
        this.shaInPassphrase = shaInPassphrase;
    }

    public String getShaOutPassphrase() {
        return shaOutPassphrase;
    }

    public void setShaOutPassphrase(String shaOutPassphrase) {
        this.shaOutPassphrase = shaOutPassphrase;
    }
}
