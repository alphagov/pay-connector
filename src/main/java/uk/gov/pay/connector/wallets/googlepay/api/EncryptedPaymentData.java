package uk.gov.pay.connector.wallets.googlepay.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.hibernate.validator.constraints.NotEmpty;

public class EncryptedPaymentData {
    @NotEmpty(message= "Field [signed_message] must not be empty")
    private final String signedMessage;
    
    @NotEmpty(message= "Field [protocol_version] must not be empty")
    private final String protocolVersion;

    @NotEmpty(message= "Field [signature] must not be empty")
    private final String signature;

    public EncryptedPaymentData(@JsonProperty("signed_message") String signedMessage,
                                @JsonProperty("protocol_version") String protocolVersion,
                                @JsonProperty("signature") String signature) {
        this.signedMessage = signedMessage;
        this.protocolVersion = protocolVersion;
        this.signature = signature;
    }

    public String getSignedMessage() {
        return signedMessage;
    }

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public String getSignature() {
        return signature;
    }
}
