package uk.gov.pay.connector.wallets.applepay.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import uk.gov.pay.connector.wallets.WalletAuthorisationRequest;
import uk.gov.pay.connector.wallets.WalletType;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApplePayAuthRequest implements WalletAuthorisationRequest {
    @NotNull
    @Valid
    private ApplePayPaymentInfo paymentInfo;

    @Schema(description = "paymentData of Apple Pay payment token as String. " +
            "This is de-serialised and decrypted for WorldPay payments. For Stripe payments, the value is passed as is when creating a token",
            example = "{\"version\":\"EC_v1\",\"data\":\"MLHhOn2BXhNw9wLLDR48DyeUcuSmRJ6KnAIGTMGqsgiMpc+AoJ…LUQ6UovkfSnW0sFH6NGZ0jhoap6LYnThYb9WT6yKfEm/rDhM=\",\"signature\":\"MIAGCSqGSIb3DQEHAqCAMIACAQExDzANBglghkgBZQMEAgEFAD…ZuQFfsLJ+Nb3+7bpjfBsZAhA1sIT1XmHoGFdoCUT3AAAAAAAA\",\"header\":{\"ephemeralPublicKey\":\"MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE5/Qc6z4TY5HQ5n…KC3kJ4DtIWedPQ70N35PBZzJUrFjtvDZFUvs80uo2ynu+lw==\",\"publicKeyHash\":\"Xzn7W3vsrlKlb0QvUAviASubdtW4BotWrDo5mGG+UWY=\",\"transactionId\":\"372c3858122b6bc39c6095eca2f994a8aa012f3b025d0d72ecfd449c2a5877f9\"}}")
    private String paymentData;

    @Override
    @JsonProperty("payment_info")
    public ApplePayPaymentInfo getPaymentInfo() {
        return paymentInfo;
    }

    public String getPaymentData() {
        return paymentData;
    }

    public ApplePayAuthRequest() {
    }

    public ApplePayAuthRequest(ApplePayPaymentInfo paymentInfo, String paymentData) {
        this.paymentInfo = paymentInfo;
        this.paymentData = paymentData;
    }

    @Override
    @Schema(hidden = true)
    public WalletType getWalletType() {
        return WalletType.APPLE_PAY;
    }
}
