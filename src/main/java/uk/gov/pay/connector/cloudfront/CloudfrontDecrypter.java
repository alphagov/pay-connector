package uk.gov.pay.connector.cloudfront;

import com.amazonaws.encryptionsdk.AwsCrypto;
import com.amazonaws.encryptionsdk.CryptoResult;
import com.amazonaws.encryptionsdk.jce.JceMasterKey;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.hibernate.validator.constraints.NotEmpty;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

public class CloudfrontDecrypter {
    @JsonProperty @NotEmpty
    private PrivateKey privateKey;
    
    @JsonProperty @NotEmpty
    private String keyName;
    
    @JsonProperty @NotEmpty
    private String keyProvider;

    public CloudfrontDecrypter(String privateKeyPem, String keyName, String keyProvider) throws NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] key = Base64.getDecoder().decode(privateKeyPem);
        this.privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(key));
        this.keyName = keyName;
        this.keyProvider = keyProvider;
    }
    
    public String decrypt(final String encryptedEncodedString) {
        final byte[] encryptedBytes = Base64.getDecoder().decode(encryptedEncodedString);
        final AwsCrypto crypto = new AwsCrypto();
        final JceMasterKey masterKey = JceMasterKey.getInstance(
                null,
                privateKey,
                keyProvider,
                keyName,
                "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
        );
        final CryptoResult<byte[], ?> result = crypto.decryptData(masterKey, encryptedBytes);
        return new String(result.getResult());
    }
}
