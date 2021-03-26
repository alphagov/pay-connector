package uk.gov.pay.connector.charge.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import uk.gov.service.payments.commons.model.Source;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.service.payments.commons.model.Source.CARD_AGENT_INITIATED_MOTO;
import static uk.gov.service.payments.commons.model.Source.CARD_API;
import static uk.gov.service.payments.commons.model.Source.CARD_PAYMENT_LINK;

public class SourceDeserialiser extends JsonDeserializer<Source> {

    private static final Set<Source> ALLOWED_SOURCES = EnumSet.of(CARD_API, CARD_PAYMENT_LINK, CARD_AGENT_INITIATED_MOTO);

    public SourceDeserialiser() {
    }

    public Source deserialize(JsonParser jsonParser, DeserializationContext ctxt) throws IOException {
        String valueAsString = jsonParser.getValueAsString();

        if (isBlank(valueAsString)) {
            return null;
        }

        return Source.from(valueAsString)
                .filter(ALLOWED_SOURCES::contains)
                .orElseThrow(() -> new JsonMappingException(null, "Field [source] must be one of CARD_API, CARD_PAYMENT_LINK, CARD_AGENT_INITIATED_MOTO"));
    }

}
