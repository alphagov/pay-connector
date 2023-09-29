package uk.gov.pay.connector.wallets.googlepay.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import javax.validation.constraints.NotEmpty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GooglePayEncryptedPaymentData {
    @NotEmpty(message= "Field [signed_message] must not be empty")
    @Schema(hidden = true)
    private final String signedMessage;
    
    @NotEmpty(message= "Field [protocol_version] must not be empty")
    @Schema(hidden = true)
    private final String protocolVersion;

    @NotEmpty(message= "Field [signature] must not be empty")
    private final String signature;

    public GooglePayEncryptedPaymentData(@Schema(example = "aSignedMessage") 
                                @JsonProperty("signed_message") String signedMessage,
                                         @Schema(example = "ECv1")
                                @JsonProperty("protocol_version") String protocolVersion,
                                         @Schema(example = "MEQCIB54h8T/hWY3864Ufkwo4SF5IjhoMV9hjpJRIsqbAn4LAiBZz1VBZ+aiaduX8MN3dBtzyDOZVstwG/8bqJZDbrhKfQ=")
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
