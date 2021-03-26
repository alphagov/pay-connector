package uk.gov.pay.connector.charge.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import uk.gov.service.payments.commons.model.Source;

import java.io.IOException;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.service.payments.commons.model.Source.CARD_API;
import static uk.gov.service.payments.commons.model.Source.CARD_PAYMENT_LINK;

public class SourceDeserialiser extends JsonDeserializer<Source> {
    public SourceDeserialiser() {
    }

    public Source deserialize(JsonParser jsonParser, DeserializationContext ctxt) throws IOException {
        String valueAsString = jsonParser.getValueAsString();

        if (isBlank(valueAsString)) {
            return null;
        }

        try {
            Source source = Source.valueOf(valueAsString);

            if (CARD_API.equals(source) || CARD_PAYMENT_LINK.equals(source)) {
                return source;
            }

            throw new JsonMappingException(null, "Field [source] must be one of CARD_API, CARD_PAYMENT_LINK");
        } catch (IllegalArgumentException e) {
            throw new JsonMappingException(null, "Field [source] must be one of CARD_API, CARD_PAYMENT_LINK");
        }
    }
}
