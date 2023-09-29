package uk.gov.pay.connector.wallets.applepay;

import uk.gov.pay.connector.wallets.applepay.api.ApplePayAuthRequest;
import uk.gov.pay.connector.wallets.applepay.api.ApplePayPaymentInfo;
import uk.gov.pay.connector.wallets.model.WalletPaymentInfo;

import java.io.IOException;

public class ApplePayAuthRequestBuilder {
    private String paymentData = "{\"data\":\"{{data}}\",\"version\":\"EC_v1\",\"header\":{\"publicKeyHash\":\"LbsUwAT6w1JV9tFXocU813TCHks+LSuFF0R/eBkrWnQ=\",\"ephemeralPublicKey\":\"{{ephemeralPublicKey}}\",\"transactionId\":\"2686f5297f123ec7fd9d31074d43d201953ca75f098890375f13aed2737d92f2\",\"application_data\":null,\"wrappedKey\":null},\"signature\":\"signature\"}";
    private String data = "4OZho15e9Yp5K0EtKergKzeRpPAjnKHwmSNnagxhjwhKQ5d29sfTXjdbh1CtTJ4DYjsD6kfulNUnYmBTsruphBz7RRVI1WI8P0LrmfTnImjcq1mi+BRN7EtR2y6MkDmAr78anff91hlc+x8eWD/NpO/oZ1ey5qV5RBy/Jp5zh6ndVUVq8MHHhvQv4pLy5Tfi57Yo4RUhAsyXyTh4x/p1360BZmoWomK15NcJfUmoUCuwEYoi7xUkRwNr1z4MKnzMfneSRpUgdc0wADMeB6u1jcuwqQnnh2cusiagOTCfD6jO6tmouvu6KO54uU7bAbKz6cocIOEAOc6keyFXG5dfw8i3hJg6G2vIefHCwcKu1zFCHr4P7jLnYFDEhvxLm1KskDcuZeQHAkBMmLRSgj9NIcpBa94VN/JTga8W75IWAA==";
    private String ephemeralPublicKey = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEMwliotf2ICjiMwREdqyHSilqZzuV2fZey86nBIDlTY8sNMJv9CPpL5/DKg4bIEMe6qaj67mz4LWdr7Er0Ld5qA==";

    private ApplePayAuthRequestBuilder() {
    }

    public static ApplePayAuthRequestBuilder anApplePayToken() throws IOException {
        return new ApplePayAuthRequestBuilder();
    }

    public ApplePayAuthRequestBuilder withData(String data) {
        this.data = data;
        return this;
    }

    public ApplePayAuthRequestBuilder withPaymentData(String paymentData) {
        this.paymentData = paymentData;
        return this;
    }

    public ApplePayAuthRequestBuilder withEphemeralPublicKey(String ephemeralPublicKey) {
        this.ephemeralPublicKey = ephemeralPublicKey;
        return this;
    }

    public String getPaymentData() {
        return paymentData
                .replace("{{data}}", data)
                .replace("{{ephemeralPublicKey}}", ephemeralPublicKey);
    }

    public ApplePayAuthRequest build() {
        return new ApplePayAuthRequest(
                new ApplePayPaymentInfo(),
                getPaymentData()
        );
    }
}
