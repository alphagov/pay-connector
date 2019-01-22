package uk.gov.pay.connector.app;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotNull;

public class GooglePayKeyManagement {

    @NotNull
    @JsonProperty("production")
    private Boolean production;

    @NotNull
    @JsonProperty("privateDecryptionKey")
    private String privateDecryptionKey;

    public Boolean isProduction() {
        return production;
    }

    public String getPrivateDecryptionKey() {
        return privateDecryptionKey;
    }
}
