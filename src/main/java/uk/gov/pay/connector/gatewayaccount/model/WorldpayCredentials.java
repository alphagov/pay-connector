package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import uk.gov.pay.connector.gatewayaccountcredentials.resource.GatewayAccountCredentialsRequestValidator;

import java.util.Objects;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class WorldpayCredentials extends GatewayCredentials {

    @JsonProperty(GatewayAccount.CREDENTIALS_MERCHANT_ID)
    private String legacyOneOffCustomerInitiatedMerchantId;

    @JsonProperty(GatewayAccount.CREDENTIALS_USERNAME)
    private String legacyOneOffCustomerInitiatedUsername;

    @JsonProperty(GatewayAccount.CREDENTIALS_PASSWORD)
    private String legacyOneOffCustomerInitiatedPassword;

    @JsonProperty(GatewayAccount.ONE_OFF_CUSTOMER_INITIATED)
    private WorldpayMerchantCodeCredentials oneOffCustomerInitiatedCredentials;

    @JsonProperty(GatewayAccount.RECURRING_CUSTOMER_INITIATED)
    private WorldpayMerchantCodeCredentials recurringCustomerInitiatedCredentials;

    @JsonProperty(GatewayAccount.RECURRING_MERCHANT_INITIATED)
    private WorldpayMerchantCodeCredentials recurringMerchantInitiatedCredentials;

    @JsonProperty(GatewayAccountCredentialsRequestValidator.FIELD_GATEWAY_MERCHANT_ID)
    private String googlePayMerchantId;

    public WorldpayCredentials() {
        // jackson
    }

    public String getLegacyOneOffCustomerInitiatedMerchantId() {
        return legacyOneOffCustomerInitiatedMerchantId;
    }

    public void setLegacyOneOffCustomerInitiatedMerchantId(String legacyOneOffCustomerInitiatedMerchantId) {
        this.legacyOneOffCustomerInitiatedMerchantId = legacyOneOffCustomerInitiatedMerchantId;
    }

    public String getLegacyOneOffCustomerInitiatedUsername() {
        return legacyOneOffCustomerInitiatedUsername;
    }

    public void setLegacyOneOffCustomerInitiatedUsername(String legacyOneOffCustomerInitiatedUsername) {
        this.legacyOneOffCustomerInitiatedUsername = legacyOneOffCustomerInitiatedUsername;
    }

    public String getLegacyOneOffCustomerInitiatedPassword() {
        return legacyOneOffCustomerInitiatedPassword;
    }

    public void setLegacyOneOffCustomerInitiatedPassword(String legacyOneOffCustomerInitiatedPassword) {
        this.legacyOneOffCustomerInitiatedPassword = legacyOneOffCustomerInitiatedPassword;
    }

    public WorldpayMerchantCodeCredentials getOneOffCustomerInitiatedCredentials() {
        return oneOffCustomerInitiatedCredentials;
    }

    public void setOneOffCustomerInitiatedCredentials(WorldpayMerchantCodeCredentials oneOffCustomerInitiatedCredentials) {
        this.oneOffCustomerInitiatedCredentials = oneOffCustomerInitiatedCredentials;
    }

    public WorldpayMerchantCodeCredentials getRecurringCustomerInitiatedCredentials() {
        return recurringCustomerInitiatedCredentials;
    }

    public void setRecurringCustomerInitiatedCredentials(WorldpayMerchantCodeCredentials recurringCustomerInitiatedCredentials) {
        this.recurringCustomerInitiatedCredentials = recurringCustomerInitiatedCredentials;
    }

    public WorldpayMerchantCodeCredentials getRecurringMerchantInitiatedCredentials() {
        return recurringMerchantInitiatedCredentials;
    }

    public void setRecurringMerchantInitiatedCredentials(WorldpayMerchantCodeCredentials recurringMerchantInitiatedCredentials) {
        this.recurringMerchantInitiatedCredentials = recurringMerchantInitiatedCredentials;
    }

    public String getGooglePayMerchantId() {
        return googlePayMerchantId;
    }

    public void setGooglePayMerchantId(String googlePayMerchantId) {
        this.googlePayMerchantId = googlePayMerchantId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        WorldpayCredentials that = (WorldpayCredentials) o;
        return Objects.equals(legacyOneOffCustomerInitiatedMerchantId, that.legacyOneOffCustomerInitiatedMerchantId)
                && Objects.equals(legacyOneOffCustomerInitiatedUsername, that.legacyOneOffCustomerInitiatedUsername)
                && Objects.equals(legacyOneOffCustomerInitiatedPassword, that.legacyOneOffCustomerInitiatedPassword)
                && Objects.equals(oneOffCustomerInitiatedCredentials, that.oneOffCustomerInitiatedCredentials)
                && Objects.equals(recurringCustomerInitiatedCredentials, that.recurringCustomerInitiatedCredentials)
                && Objects.equals(recurringMerchantInitiatedCredentials, that.recurringMerchantInitiatedCredentials)
                && Objects.equals(googlePayMerchantId, that.googlePayMerchantId);

    }

    @Override
    public int hashCode() {
        return Objects.hash(legacyOneOffCustomerInitiatedMerchantId, legacyOneOffCustomerInitiatedUsername,
                legacyOneOffCustomerInitiatedPassword, oneOffCustomerInitiatedCredentials,
                recurringCustomerInitiatedCredentials, recurringMerchantInitiatedCredentials, googlePayMerchantId);
    }

}
