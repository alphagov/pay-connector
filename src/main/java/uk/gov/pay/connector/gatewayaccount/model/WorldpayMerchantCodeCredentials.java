package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Objects;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class WorldpayMerchantCodeCredentials {

    private String merchantCode;
    private String username;
    private String password;

    public WorldpayMerchantCodeCredentials() {
        // jackson
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
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        WorldpayMerchantCodeCredentials that = (WorldpayMerchantCodeCredentials) o;
        return Objects.equals(merchantCode, that.merchantCode)
                && Objects.equals(username, that.username)
                && Objects.equals(password, that.password);
    }

    @Override
    public int hashCode() {
        return Objects.hash(merchantCode, username, password);
    }

}
