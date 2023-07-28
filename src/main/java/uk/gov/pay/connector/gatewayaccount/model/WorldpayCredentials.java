package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import uk.gov.pay.connector.gatewayaccountcredentials.resource.GatewayAccountCredentialsRequestValidator;

import java.util.Objects;
import java.util.Optional;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorldpayCredentials implements GatewayCredentials {

    @JsonProperty(GatewayAccount.CREDENTIALS_MERCHANT_ID)
    @Schema(hidden = true)
    private String legacyOneOffCustomerInitiatedMerchantCode;

    @JsonProperty(GatewayAccount.CREDENTIALS_USERNAME)
    @Schema(hidden = true)
    private String legacyOneOffCustomerInitiatedUsername;

    @JsonProperty(GatewayAccount.CREDENTIALS_PASSWORD)
    @Schema(hidden = true)
    private String legacyOneOffCustomerInitiatedPassword;

    private WorldpayMerchantCodeCredentials oneOffCustomerInitiatedCredentials;

    private WorldpayMerchantCodeCredentials recurringCustomerInitiatedCredentials;

    private WorldpayMerchantCodeCredentials recurringMerchantInitiatedCredentials;

    private String googlePayMerchantId;

    public WorldpayCredentials() {
        // Janet Jackson
    }

    public void setLegacyOneOffCustomerInitiatedMerchantCode(String legacyOneOffCustomerInitiatedMerchantCode) {
        this.legacyOneOffCustomerInitiatedMerchantCode = legacyOneOffCustomerInitiatedMerchantCode;
    }

    public void setLegacyOneOffCustomerInitiatedUsername(String legacyOneOffCustomerInitiatedUsername) {
        this.legacyOneOffCustomerInitiatedUsername = legacyOneOffCustomerInitiatedUsername;
    }

    public void setLegacyOneOffCustomerInitiatedPassword(String legacyOneOffCustomerInitiatedPassword) {
        this.legacyOneOffCustomerInitiatedPassword = legacyOneOffCustomerInitiatedPassword;
    }

    @JsonIgnore
    public Optional<WorldpayMerchantCodeCredentials> getOneOffCustomerInitiatedCredentials() {
        if (oneOffCustomerInitiatedCredentials == null && legacyOneOffCustomerInitiatedMerchantCode != null) {
            return Optional.of(new WorldpayMerchantCodeCredentials(
                    legacyOneOffCustomerInitiatedMerchantCode,
                    legacyOneOffCustomerInitiatedUsername,
                    legacyOneOffCustomerInitiatedPassword
            ));
        }

        return Optional.ofNullable(oneOffCustomerInitiatedCredentials);
    }

    @JsonProperty(GatewayAccount.ONE_OFF_CUSTOMER_INITIATED)
    @JsonView({Views.Api.class})
    private WorldpayMerchantCodeCredentials getOneOffCustomerInitiatedCredentialsForSerialization() {
        return getOneOffCustomerInitiatedCredentials().orElse(null);
    }

    @JsonProperty(GatewayAccount.ONE_OFF_CUSTOMER_INITIATED)
    public void setOneOffCustomerInitiatedCredentials(WorldpayMerchantCodeCredentials oneOffCustomerInitiatedCredentials) {
        this.oneOffCustomerInitiatedCredentials = oneOffCustomerInitiatedCredentials;
    }

    @JsonIgnore
    public Optional<WorldpayMerchantCodeCredentials> getRecurringCustomerInitiatedCredentials() {
        return Optional.ofNullable(recurringCustomerInitiatedCredentials);
    }

    @JsonProperty(GatewayAccount.RECURRING_CUSTOMER_INITIATED)
    @JsonView({Views.Api.class})
    private WorldpayMerchantCodeCredentials getRecurringCustomerInitiatedCredentialsForSerialization() {
        return getRecurringCustomerInitiatedCredentials().orElse(null);
    }

    @JsonProperty(GatewayAccount.RECURRING_CUSTOMER_INITIATED)
    public void setRecurringCustomerInitiatedCredentials(WorldpayMerchantCodeCredentials recurringCustomerInitiatedCredentials) {
        this.recurringCustomerInitiatedCredentials = recurringCustomerInitiatedCredentials;
    }

    @JsonIgnore
    public Optional<WorldpayMerchantCodeCredentials> getRecurringMerchantInitiatedCredentials() {
        return Optional.ofNullable(recurringMerchantInitiatedCredentials);
    }

    @JsonProperty(GatewayAccount.RECURRING_MERCHANT_INITIATED)
    @JsonView({Views.Api.class})
    private WorldpayMerchantCodeCredentials getRecurringMerchantInitiatedCredentialsForSerialization() {
        return getRecurringMerchantInitiatedCredentials().orElse(null);
    }

    @JsonProperty(GatewayAccount.RECURRING_MERCHANT_INITIATED)
    public void setRecurringMerchantInitiatedCredentials(WorldpayMerchantCodeCredentials recurringMerchantInitiatedCredentials) {
        this.recurringMerchantInitiatedCredentials = recurringMerchantInitiatedCredentials;
    }

    @Override
    @JsonIgnore
    public Optional<String> getGooglePayMerchantId() {
        return Optional.ofNullable(googlePayMerchantId);
    }

    @JsonProperty(GatewayAccountCredentialsRequestValidator.FIELD_GATEWAY_MERCHANT_ID)
    @JsonView({Views.Api.class})
    private String getGooglePayMerchantIdForSerialization() {
        return getGooglePayMerchantId().orElse(null);
    }

    @JsonProperty(GatewayAccountCredentialsRequestValidator.FIELD_GATEWAY_MERCHANT_ID)
    public void setGooglePayMerchantId(String googlePayMerchantId) {
        this.googlePayMerchantId = googlePayMerchantId;
    }

    @Override
    public boolean hasCredentials() {
        return legacyOneOffCustomerInitiatedMerchantCode != null
                || oneOffCustomerInitiatedCredentials != null
                || (recurringCustomerInitiatedCredentials != null && recurringMerchantInitiatedCredentials != null);
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
