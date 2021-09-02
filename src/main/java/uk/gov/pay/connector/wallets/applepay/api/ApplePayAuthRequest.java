package uk.gov.pay.connector.wallets.applepay.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import uk.gov.pay.connector.wallets.WalletAuthorisationRequest;
import uk.gov.pay.connector.wallets.model.WalletPaymentInfo;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApplePayAuthRequest implements WalletAuthorisationRequest {
    @NotNull @Valid private WalletPaymentInfo paymentInfo;
    private EncryptedPaymentData encryptedPaymentData;

    @JsonProperty("payment_info")
    public WalletPaymentInfo getPaymentInfo() {
        return paymentInfo;
    }

    public EncryptedPaymentData getEncryptedPaymentData() {
        return encryptedPaymentData;
    }

    public ApplePayAuthRequest() {
    }

    public ApplePayAuthRequest(WalletPaymentInfo paymentInfo, EncryptedPaymentData encryptedPaymentData) {
        this.paymentInfo = paymentInfo;
        this.encryptedPaymentData = encryptedPaymentData;
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EncryptedPaymentData {
        private String data;
        private String version;
        private Header header;
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

        public EncryptedPaymentData() {
        }

        public EncryptedPaymentData(String version, String data, Header header, String signature) {
            this.data = data;
            this.version = version;
            this.header = header;
            this.signature = signature;
        }

        @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Header {
            private String publicKeyHash;
            private String ephemeralPublicKey;
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
