package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class WorldpayMerchantCodeCredentials {

    private String merchantCode;
    private String username;
    private String password;

    public WorldpayMerchantCodeCredentials() {
        // Janet Jackson
    }

    public WorldpayMerchantCodeCredentials(String merchantCode, String username, String password) {
        this.merchantCode = merchantCode;
        this.username = username;
        this.password = password;
    }

    public String getMerchantCode() {
        return merchantCode;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other == null || getClass() != other.getClass()) {
            return false;
        }

        var that = (WorldpayMerchantCodeCredentials) other;

        return Objects.equals(merchantCode, that.merchantCode)
                && Objects.equals(username, that.username)
                && Objects.equals(password, that.password);
    }

    @Override
    public int hashCode() {
        return Objects.hash(merchantCode, username, password);
    }

}
