package uk.gov.pay.connector.wallets.applepay.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import uk.gov.pay.connector.wallets.WalletAuthorisationRequest;
import uk.gov.pay.connector.wallets.model.WalletPaymentInfo;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApplePayAuthRequest implements WalletAuthorisationRequest {
    @NotNull
    @Valid
    private WalletPaymentInfo paymentInfo;

    @Schema(name = "encrypted_payment_data")
    private ApplePayEncryptedPaymentData encryptedPaymentData;

    @JsonProperty("payment_info")
    public WalletPaymentInfo getPaymentInfo() {
        return paymentInfo;
    }

    public ApplePayEncryptedPaymentData getEncryptedPaymentData() {
        return encryptedPaymentData;
    }

    public ApplePayAuthRequest() {
    }

    public ApplePayAuthRequest(WalletPaymentInfo paymentInfo, ApplePayEncryptedPaymentData encryptedPaymentData) {
        this.paymentInfo = paymentInfo;
        this.encryptedPaymentData = encryptedPaymentData;
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(value = JsonInclude.Include.NON_NULL)
    public static class ApplePayEncryptedPaymentData {
        @Schema(example = "4OZho15e9Yp5K0EtKergKzeRpPAjnK...JTga8W75IWAA==")
        private String data;
        @Schema(example = "ECv1")
        private String version;
        private Header header;
        @Schema(example = "MIAGCSqGSIb3DQEHAqCAMIACAQE..../tJr3SbTdxO25ZdN1bPH0Jiqgw7AAAAAAAA")
        private String signature;


        public String getSignature() {
            return signature;
        }

        public String getData() {
            return data;
        }

        public String getVersion() {
            return version;
        }

        public Header getHeader() {
            return header;
        }

        public ApplePayEncryptedPaymentData() {
        }

        public ApplePayEncryptedPaymentData(String version, String data, Header header, String signature) {
            this.data = data;
            this.version = version;
            this.header = header;
            this.signature = signature;
        }

        @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
        @JsonIgnoreProperties(ignoreUnknown = true)
        @JsonInclude(value = JsonInclude.Include.NON_NULL)
        public static class Header {
            @Schema(example = "LbsUwAT6w1JV9tFXocU813TCHks+LSuFF0R/eBkrWnQ=")
            private String publicKeyHash;
            @Schema(example = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEMwliotf2ICjiMwREdqyHSilqZzuV2fZey86nBIDlTY8sNMJv9CPpL5/DKg4bIEMe6qaj67mz4LWdr7Er0Ld5qA==")
            private String ephemeralPublicKey;
            @Schema(example = "2686f5297f123ec7fd9d31074d43d201953ca75f098890375f13aed2737d92f2")
            private String transactionId;
            private String applicationData;
            private String wrappedKey;

            public Header() {
            }

            public Header(String publicKeyHash, String ephemeralPublicKey, String transactionId, String applicationData, String wrappedKey) {
                this.publicKeyHash = publicKeyHash;
                this.ephemeralPublicKey = ephemeralPublicKey;
                this.transactionId = transactionId;
                this.applicationData = applicationData;
                this.wrappedKey = wrappedKey;
            }

            public String getPublicKeyHash() {
                return publicKeyHash;
            }

            public String getEphemeralPublicKey() {
                return ephemeralPublicKey;
            }

            public String getTransactionId() {
                return transactionId;
            }

            public String getApplicationData() {
                return applicationData;
            }

            public String getWrappedKey() {
                return wrappedKey;
            }
        }
    }
}
