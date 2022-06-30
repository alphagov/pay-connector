package uk.gov.pay.connector.wallets.applepay;

import com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.pay.connector.wallets.applepay.api.ApplePayAuthRequest;
import uk.gov.pay.connector.wallets.model.WalletPaymentInfo;

import java.io.IOException;

import static io.dropwizard.testing.FixtureHelpers.fixture;

public class ApplePayAuthRequestBuilder {
    private String data;
    private String ephemeralPublicKey;
    private ApplePayAuthRequest.ApplePayEncryptedPaymentData validEncryptedPaymentData;
    
    private ApplePayAuthRequestBuilder() throws IOException {
        this.validEncryptedPaymentData = new ObjectMapper().readValue(fixture("applepay/token.json"), ApplePayAuthRequest.ApplePayEncryptedPaymentData.class);
        this.data = validEncryptedPaymentData.getData();
        this.ephemeralPublicKey = validEncryptedPaymentData.getHeader().getEphemeralPublicKey();
    }
    
    public static ApplePayAuthRequestBuilder anApplePayToken() throws IOException {
        return new ApplePayAuthRequestBuilder();
    }
    public ApplePayAuthRequestBuilder withData(String data) {
        this.data = data;
        return this;
    }

    public ApplePayAuthRequestBuilder withEphemeralPublicKey(String ephemeralPublicKey) {
        this.ephemeralPublicKey = ephemeralPublicKey;
        return this;
    }

    public ApplePayAuthRequest build() {
        return new ApplePayAuthRequest(
                new WalletPaymentInfo(),
                new ApplePayAuthRequest.ApplePayEncryptedPaymentData(
                        validEncryptedPaymentData.getVersion(),
                        this.data,
                        new ApplePayAuthRequest.ApplePayEncryptedPaymentData.Header(
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
