package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.Objects;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class WorldpayCredentials {
    
    @NotEmpty(message = "Field [merchant_id] is required")
    private String merchantId;

    @NotEmpty(message = "Field [username] is required")
    private String username;

    @NotEmpty(message = "Field [password] is required")
    private String password;
    
    public WorldpayCredentials() {
        // Blank constructor needed for deserialization
    }

    public WorldpayCredentials(String merchantId, String username, String password) {
        this.merchantId = merchantId;
        this.username = username;
        this.password = password;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorldpayCredentials that = (WorldpayCredentials) o;
        return Objects.equals(merchantId, that.merchantId) &&
                Objects.equals(username, that.username) &&
                Objects.equals(password, that.password);
    }

    @Override
    public int hashCode() {
        return Objects.hash(merchantId, username, password);
    }
}
