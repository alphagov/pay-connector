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
import static uk.gov.service.payments.commons.model.Source.*;

public class AuthModeDeserialiser extends JsonDeserializer<AuthMode> {

    private static final Set<AuthMode> ALLOWED_AUTH_MODES = EnumSet.of(AuthMode.API, AuthMode.WEB);

    public AuthModeDeserialiser() {
    }

    public AuthMode deserialize(JsonParser jsonParser, DeserializationContext ctxt) throws IOException {
        String valueAsString = jsonParser.getValueAsString();

        if (isBlank(valueAsString)) {
            return null;
        }

        return AuthMode.from(valueAsString)
                .filter(ALLOWED_AUTH_MODES::contains)
                .orElseThrow(() -> new JsonMappingException(null, "Field [auth_mode] must be one of API, WEB"));
    }

}
