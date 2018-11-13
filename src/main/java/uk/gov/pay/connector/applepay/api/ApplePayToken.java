package uk.gov.pay.connector.applepay.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class ApplePayToken  {
    private ApplePaymentInfo applePaymentInfo;
    private EncryptedPaymentData encryptedPaymentData;

    public ApplePaymentInfo getApplePaymentInfo() {
        return applePaymentInfo;
    }

    public EncryptedPaymentData getEncryptedPaymentData() {
        return encryptedPaymentData;
    }

    public ApplePayToken() {
    }

    public ApplePayToken(ApplePaymentInfo applePaymentInfo, EncryptedPaymentData encryptedPaymentData) {
        this.applePaymentInfo = applePaymentInfo;
        this.encryptedPaymentData = encryptedPaymentData;
    }

    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
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

        @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
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
