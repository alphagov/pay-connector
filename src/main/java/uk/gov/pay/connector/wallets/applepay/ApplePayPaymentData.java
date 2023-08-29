package uk.gov.pay.connector.wallets.applepay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ApplePayPaymentData {

    @JsonProperty("data")
    private String data;

    @JsonProperty("version")
    private String version;
    @JsonProperty("header")
    private Header header;
    @JsonProperty("signature")
    private String signature;

    public ApplePayPaymentData() {

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

    public String getSignature() {
        return signature;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Header {
        @JsonProperty("publicKeyHash")
        private String publicKeyHash;
        @JsonProperty("ephemeralPublicKey")
        private String ephemeralPublicKey;
        @JsonProperty("transactionId")
        private String transactionId;
        @JsonProperty("applicationData")
        private String applicationData;
        @JsonProperty("wrappedKey")
        private String wrappedKey;

        public Header() {

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
