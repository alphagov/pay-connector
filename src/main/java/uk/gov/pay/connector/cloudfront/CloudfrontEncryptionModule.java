package uk.gov.pay.connector.cloudfront;

import com.fasterxml.jackson.databind.module.SimpleModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

public class CloudfrontEncryptionModule extends SimpleModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(CloudfrontEncryptionModule.class);
    
    public CloudfrontEncryptionModule() {
        final CloudfrontEncryptedFieldDeserializer encryptedFieldDeserializer;
        
        String privateKeyPem = System.getenv("CLOUDFRONT_PRIVATE_KEY");
        String keyName = System.getenv("CLOUDFRONT_KEY_NAME");
        String keyProvider = System.getenv("CLOUDFRONT_KEY_PROVIDER");
        
        if (privateKeyPem != null && keyName != null && keyProvider != null) {
            try {
                encryptedFieldDeserializer = new CloudfrontEncryptedFieldDeserializer(new CloudfrontDecrypter(privateKeyPem, keyName, keyProvider));
            } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                throw new RuntimeException("Failed to initialise CloudfrontDecrypter", e);
            }
            LOGGER.warn("Found cloudfront service binding - enabling decryption of encrypted fields");
        } else {
            LOGGER.warn("No cloudfront service binding - assuming no field level encryption");
            encryptedFieldDeserializer = new CloudfrontEncryptedFieldDeserializer();
        }

        this.addDeserializer(CloudfrontEncryptedField.class, encryptedFieldDeserializer);
    }
}
