package uk.gov.pay.connector.wallets.googlepay.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class EncryptedPaymentData {
    @NotNull @Valid private final SignedMessage signedMessage;
    @NotNull @Valid private final IntermediateSigningKey intermediateSigningKey;
    @NotNull @Valid private final Token token;
    @NotEmpty private final String type;
    @NotEmpty private final String protocolVersion;

    public EncryptedPaymentData(@JsonProperty("signedMessage") SignedMessage signedMessage,
                                @JsonProperty("intermediateSigningKey") IntermediateSigningKey intermediateSigningKey,
                                @JsonProperty("token") Token token,
                                @JsonProperty("type") String type,
                                @JsonProperty("protocolVersion") String protocolVersion) {
        this.signedMessage = signedMessage;
        this.intermediateSigningKey = intermediateSigningKey;
        this.token = token;
        this.type = type;
        this.protocolVersion = protocolVersion;
    }

    public SignedMessage getSignedMessage() {
        return signedMessage;
    }

    public IntermediateSigningKey getIntermediateSigningKey() {
        return intermediateSigningKey;
    }

    public String getType() {
        return type;
    }

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public Token getToken() {
        return token;
    }

    public static class SignedMessage {
        @NotEmpty private final String encryptedMessage;
        @NotEmpty private final String ephemeralPublicKey;
        @NotEmpty private final String tag;

        public SignedMessage(@JsonProperty("encryptedMessage") String encryptedMessage,
                             @JsonProperty("ephemeralPublicKey") String ephemeralPublicKey,
                             @JsonProperty("tag") String tag) {
            this.encryptedMessage = encryptedMessage;
            this.ephemeralPublicKey = ephemeralPublicKey;
            this.tag = tag;
        }

        public String getEncryptedMessage() {
            return encryptedMessage;
        }

        public String getEphemeralPublicKey() {
            return ephemeralPublicKey;
        }

        public String getTag() {
            return tag;
        }
    }

    public static class IntermediateSigningKey {
        @NotNull @Valid private final IntermediateSigningKey.SignedKey signedKey;
        @NotEmpty private final String[] signatures;

        public IntermediateSigningKey(@JsonProperty("signedKey") IntermediateSigningKey.SignedKey signedKey,
                                      @JsonProperty("signatures") String[] signatures) {
            this.signedKey = signedKey;
            this.signatures = signatures;
        }

        public IntermediateSigningKey.SignedKey getSignedKey() {
            return signedKey;
        }

        public String[] getSignatures() {
            return signatures;
        }

        public static class SignedKey {
            @NotEmpty private final String key;
            @NotEmpty private final String expirationDate;

            public SignedKey(@JsonProperty("keyValue") String key,
                             @JsonProperty("keyExpiration") String expirationDate) {
                this.key = key;
                this.expirationDate = expirationDate;
            }

            public String getKey() {
                return key;
            }

            public String getExpirationDate() {
                return expirationDate;
            }
        }
    }

    public static class Token {
        @NotEmpty private final String signature;

        public Token(@JsonProperty("signature") String signature) {
            this.signature = signature;
        }

        public String getSignature() {
            return signature;
        }
    }
}
