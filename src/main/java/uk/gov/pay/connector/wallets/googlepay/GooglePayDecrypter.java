package uk.gov.pay.connector.wallets.googlepay;

import com.google.common.collect.ImmutableMap;
import com.google.crypto.tink.apps.paymentmethodtoken.PaymentMethodTokenRecipient;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import org.json.JSONObject;
import uk.gov.pay.connector.wallets.googlepay.api.EncryptedPaymentData;
import uk.gov.pay.connector.wallets.PaymentData;

import java.security.GeneralSecurityException;

import static com.google.crypto.tink.apps.paymentmethodtoken.GooglePaymentsPublicKeysManager.INSTANCE_PRODUCTION;
import static com.google.crypto.tink.apps.paymentmethodtoken.GooglePaymentsPublicKeysManager.INSTANCE_TEST;

public class GooglePayDecrypter {

    /*
    PAN_ONLY encrypted message should look like
    {
      "paymentMethod": "CARD",
      "paymentMethodDetails": {
        "authMethod": "PAN_ONLY",
        "pan": "1111222233334444",
        "expirationMonth": 10,
        "expirationYear": 2020
      },
      "messageId": "some-message-id",
      "messageExpiration": "1577862000000"
    }
    
    CRYPTOGRAM_3DS encrypted message should look like the above but "paymentMethodDetails" should look like
    {
      "authMethod": "CRYPTOGRAM_3DS",
      "pan": "1111222233334444",
      "expirationMonth": 10,
      "expirationYear": 2020,
      "cryptogram": "AAAAAA...",
      "eciIndicator": "eci indicator"
    }
     */
    public PaymentData decrypt(EncryptedPaymentData encryptedPaymentData, String privateKey, boolean isProduction, String merchantId) throws GeneralSecurityException {
        String decryptedMessage = new PaymentMethodTokenRecipient.Builder()
                .fetchSenderVerifyingKeysWith(isProduction ? INSTANCE_PRODUCTION : INSTANCE_TEST)
                .recipientId("merchant:" + merchantId)
                .protocolVersion("ECv2")
                .addRecipientPrivateKey(privateKey)
                .build()
                .unseal(createEncryptedMessage(encryptedPaymentData));
        Object document = Configuration.defaultConfiguration().jsonProvider().parse(decryptedMessage);
        return new PaymentData(
                getStringValue(document, "$.paymentMethodDetails.cryptogram"), 
                getStringValue(document, "$.paymentMethodDetails.eciIndicator"), 
                getStringValue(document, "$.paymentMethodDetails.pan"));
    }

    private static String createEncryptedMessage(EncryptedPaymentData tokenizationData) {
        JSONObject encryptedMessageJson = new JSONObject();
        encryptedMessageJson.put("signature", tokenizationData.getToken().getSignature());
        encryptedMessageJson.put("intermediateSigningKey", ImmutableMap.of(
                "signedKey", tokenizationData.getIntermediateSigningKey().getSignedKey(),
                "signatures", tokenizationData.getIntermediateSigningKey().getSignatures()));
        encryptedMessageJson.put("protocolVersion", tokenizationData.getProtocolVersion());
        encryptedMessageJson.put("signedMessage", tokenizationData.getSignedMessage());
        return encryptedMessageJson.toString();
    }

    private static String getStringValue(Object document, String jsonPath) {
        try {
            return JsonPath.read(document, jsonPath);
        } catch (PathNotFoundException e) {
            return null;
        }
    }
}
