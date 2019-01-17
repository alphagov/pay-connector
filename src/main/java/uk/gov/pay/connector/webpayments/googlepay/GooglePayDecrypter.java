package uk.gov.pay.connector.webpayments.googlepay;

import com.google.common.collect.ImmutableMap;
import com.google.crypto.tink.apps.paymentmethodtoken.GooglePaymentsPublicKeysManager;
import com.google.crypto.tink.apps.paymentmethodtoken.PaymentMethodTokenRecipient;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import org.json.JSONObject;
import uk.gov.pay.connector.webpayments.PaymentData;

import java.security.GeneralSecurityException;

public class GooglePayDecrypter {
    
    public PaymentData decrypt(String tokenizationData, String privateKey) throws GeneralSecurityException {
        String decryptedMessage = new PaymentMethodTokenRecipient.Builder()
                .fetchSenderVerifyingKeysWith(GooglePaymentsPublicKeysManager.INSTANCE_TEST)
                .recipientId("merchant:")
                .protocolVersion("ECv2")
                .addRecipientPrivateKey(privateKey)
                .build()
                .unseal(getEncryptedMessageJsonFromTokenizationData(tokenizationData));
        return null;
    }
    
    private static String getEncryptedMessageJsonFromTokenizationData(String tokenizationData) {
        Object document = Configuration.defaultConfiguration().jsonProvider().parse(tokenizationData);
        JSONObject encryptedMessageJson = new JSONObject();
        encryptedMessageJson.put("signature", getStringValue(document, "$.tokenizationData.token.signature"));
        encryptedMessageJson.put("intermediateSigningKey", ImmutableMap.of(
                "signedKey", getStringValue(document, "$.tokenizationData.intermediateSigningKey.signedKey"), 
                "signatures", new String[]{getStringValue(document, "$.tokenizationData.intermediateSigningKey.signatures[0]")}));
        encryptedMessageJson.put("protocolVersion", getStringValue(document, "$.tokenizationData.protocolVersion"));
        encryptedMessageJson.put("signedMessage", getStringValue(document, "$.tokenizationData.signedMessage"));
        return encryptedMessageJson.toString();
    }
    
    private static String getStringValue(Object document, String jsonPath) {
        return JsonPath.read(document, jsonPath);
    }
}
