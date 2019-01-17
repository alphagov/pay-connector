package uk.gov.pay.connector.applepay;

import com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.pay.connector.applepay.api.ApplePayAuthRequest;
import uk.gov.pay.connector.common.model.api.PaymentInfo;

import java.io.IOException;

import static io.dropwizard.testing.FixtureHelpers.fixture;

public class ApplePayAuthRequestBuilder {
    private String data;
    private String ephemeralPublicKey;
    private ApplePayAuthRequest.EncryptedPaymentData validEncryptedPaymentData;
    
    private ApplePayAuthRequestBuilder() throws IOException {
        this.validEncryptedPaymentData = new ObjectMapper().readValue(fixture("applepay/token.json"), ApplePayAuthRequest.EncryptedPaymentData.class);
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
                new PaymentInfo(),
                new ApplePayAuthRequest.EncryptedPaymentData(
                        validEncryptedPaymentData.getVersion(),
                        this.data,
                        new ApplePayAuthRequest.EncryptedPaymentData.Header(
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
