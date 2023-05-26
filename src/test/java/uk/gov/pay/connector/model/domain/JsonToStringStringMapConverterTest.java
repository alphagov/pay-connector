package uk.gov.pay.connector.model.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PGobject;
import uk.gov.pay.connector.gatewayaccount.util.JsonToStringStringMapConverter;

import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

class JsonToStringStringMapConverterTest {

    private static ObjectMapper objectMapper = new ObjectMapper();
    
    private JsonToStringStringMapConverter jsonToStringStringMapConverter = new JsonToStringStringMapConverter();
    private PGobject pGobject;

    @BeforeEach
    void setUp(){
        pGobject = new PGobject();
        pGobject.setType("json");
    }

    @Test
    void shouldReturnAMapContainingTheValuesOfAPGObject() throws Exception {
        JsonNode payload = objectMapper.valueToTree(
                Map.of("json_field_1", "json_value_1","json_field_2", "json_value_2"));

        Map<String, String> values = Map.of("json_field_1", "json_value_1", "json_field_2", "json_value_2");
        pGobject.setValue(objectMapper.writeValueAsString(values));

        Map<String, String> jsonValues = jsonToStringStringMapConverter.convertToEntityAttribute(pGobject);

        assertThat(objectMapper.writeValueAsString(jsonValues), is(payload.toString()));
    }

    @Test
    void shouldReturnNoMapWhenDBFieldIsNull() throws Exception {
        pGobject.setValue(null);
        Map<String, String> jsonValues = jsonToStringStringMapConverter.convertToEntityAttribute(pGobject);

        assertThat(jsonValues, is(nullValue()));
    }

    @Test
    void shouldReturnPGObjectContainingPredifinedValues() throws Exception {
        Map<String, String> values = Map.of("json_field_1", "json_value_1", "json_field_2", "json_value_2");
        pGobject = jsonToStringStringMapConverter.convertToDatabaseColumn(values);

        assertThat(pGobject.getType(), is("json"));
        assertThat(pGobject.getValue(), is(objectMapper.writeValueAsString(values)));
    }

    @Test
    void shouldReturnNullWhenEmptyMap() {
        pGobject = jsonToStringStringMapConverter.convertToDatabaseColumn(Map.of());
        assertThat(pGobject.getValue(), is(nullValue()));
    }
}
