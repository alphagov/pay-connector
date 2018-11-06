package uk.gov.pay.connector.applepay.api;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;

public class AppleCardExpiryDateDeserialiser extends StdDeserializer<AppleCardExpiryDate> {
    public AppleCardExpiryDateDeserialiser() {
        this(null);
    }

    public AppleCardExpiryDateDeserialiser(Class<?> vc) {
        super(vc);
    }

    @Override
    public AppleCardExpiryDate deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
        JsonNode node = jsonParser.getCodec().readTree(jsonParser);
        String date = node.get("applicationExpirationDate").asText();
        return new AppleCardExpiryDate(date);
    }
}
