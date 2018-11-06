package uk.gov.pay.connector.applepay.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class ApplePayToken  {
    private PaymentInfo paymentInfo;
    private EncryptedPaymentData encryptedPaymentData;

    public PaymentInfo getPaymentInfo() {
        return paymentInfo;
    }

    public EncryptedPaymentData getEncryptedPaymentData() {
        return encryptedPaymentData;
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

        @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
        public static class Header {
            private String publicKeyHash;
            private String ephemeralPublicKey;
            private String transactionId;

            public String getPublicKeyHash() {
                return publicKeyHash;
            }

            public String getEphemeralPublicKey() {
                return ephemeralPublicKey;
            }

            public String getTransactionId() {
                return transactionId;
            }
        }
    }
}
