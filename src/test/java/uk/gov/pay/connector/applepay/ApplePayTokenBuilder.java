package uk.gov.pay.connector.applepay;

import com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.pay.connector.applepay.api.ApplePayToken;
import uk.gov.pay.connector.applepay.api.ApplePaymentInfo;

import java.io.IOException;

import static io.dropwizard.testing.FixtureHelpers.fixture;

public class ApplePayTokenBuilder {
    private String data;
    private String ephemeralPublicKey;
    private ApplePayToken.EncryptedPaymentData validEncryptedPaymentData;
    
    private ApplePayTokenBuilder() throws IOException {
        this.validEncryptedPaymentData = new ObjectMapper().readValue(fixture("applepay/token.json"), ApplePayToken.EncryptedPaymentData.class);
        this.data = validEncryptedPaymentData.getData();
        this.ephemeralPublicKey = validEncryptedPaymentData.getHeader().getEphemeralPublicKey();
    }
    
    public static ApplePayTokenBuilder anApplePayToken() throws IOException {
        return new ApplePayTokenBuilder();
    }
    public ApplePayTokenBuilder withData(String data) {
        this.data = data;
        return this;
    }

    public ApplePayTokenBuilder withEphemeralPublicKey(String ephemeralPublicKey) {
        this.ephemeralPublicKey = ephemeralPublicKey;
        return this;
    }

    public ApplePayToken build() {
        return new ApplePayToken(
                new ApplePaymentInfo(),
                new ApplePayToken.EncryptedPaymentData(
                        validEncryptedPaymentData.getVersion(),
                        this.data,
                        new ApplePayToken.EncryptedPaymentData.Header(
                                validEncryptedPaymentData.getHeader().getPublicKeyHash(),
                                this.ephemeralPublicKey,
                                validEncryptedPaymentData.getHeader().getTransactionId(),
                                validEncryptedPaymentData.getHeader().getApplicationData(),
                                validEncryptedPaymentData.getHeader().getWrappedKey()
                        ),
                        validEncryptedPaymentData.getSignature()
                )
        );
    }
}
