package uk.gov.pay.connector.cloudfront;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

public class CloudfrontEncryptedFieldDeserializer extends JsonDeserializer<CloudfrontEncryptedField> {
    private final CloudfrontDecrypter cloudfrontDecrypter;

    public CloudfrontEncryptedFieldDeserializer() {
        this(null);
    }
    
    public CloudfrontEncryptedFieldDeserializer(final CloudfrontDecrypter cloudfrontDecrypter) {
        this.cloudfrontDecrypter = cloudfrontDecrypter;
    }
    
    @Override
    public CloudfrontEncryptedField deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
        String encryptedEncodedString = jsonParser.readValueAs(String.class);
        String decrypted = (cloudfrontDecrypter == null) ? encryptedEncodedString : cloudfrontDecrypter.decrypt(encryptedEncodedString);
        return new CloudfrontEncryptedField(decrypted);
    }
}
