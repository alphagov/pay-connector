package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.pay.connector.gatewayaccountcredentials.resource.GatewayAccountCredentialsRequestValidator;

import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class WorldpayCredentials implements GatewayCredentials {

    @JsonProperty(GatewayAccount.CREDENTIALS_MERCHANT_ID)
    private String legacyOneOffCustomerInitiatedMerchantCode;

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
            // Janet Jackson
    }

    public String getLegacyOneOffCustomerInitiatedMerchantCode() {
        return legacyOneOffCustomerInitiatedMerchantCode;
    }

    public void setLegacyOneOffCustomerInitiatedMerchantCode(String legacyOneOffCustomerInitiatedMerchantCode) {
        this.legacyOneOffCustomerInitiatedMerchantCode = legacyOneOffCustomerInitiatedMerchantCode;
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
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other == null || getClass() != other.getClass()) {
            return false;
        }

        var that = (WorldpayCredentials) other;

        return Objects.equals(legacyOneOffCustomerInitiatedMerchantCode, that.legacyOneOffCustomerInitiatedMerchantCode)
                && Objects.equals(legacyOneOffCustomerInitiatedUsername, that.legacyOneOffCustomerInitiatedUsername)
                && Objects.equals(legacyOneOffCustomerInitiatedPassword, that.legacyOneOffCustomerInitiatedPassword)
                && Objects.equals(oneOffCustomerInitiatedCredentials, that.oneOffCustomerInitiatedCredentials)
                && Objects.equals(recurringCustomerInitiatedCredentials, that.recurringCustomerInitiatedCredentials)
                && Objects.equals(recurringMerchantInitiatedCredentials, that.recurringMerchantInitiatedCredentials)
                && Objects.equals(googlePayMerchantId, that.googlePayMerchantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(legacyOneOffCustomerInitiatedMerchantCode, legacyOneOffCustomerInitiatedUsername,
                legacyOneOffCustomerInitiatedPassword, oneOffCustomerInitiatedCredentials,
                recurringCustomerInitiatedCredentials, recurringMerchantInitiatedCredentials, googlePayMerchantId);
    }

}
