package uk.gov.pay.connector.model.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.postgresql.util.PGobject;
import uk.gov.pay.connector.gatewayaccount.util.JsonToStringStringMapConverter;

import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

public class JsonToStringStringMapConverterTest {

    private static ObjectMapper objectMapper = new ObjectMapper();
    
    private JsonToStringStringMapConverter jsonToStringStringMapConverter = new JsonToStringStringMapConverter();
    private PGobject pGobject;

    @Before
    public void setUp(){
        pGobject = new PGobject();
        pGobject.setType("json");
    }

    @Test
    public void shouldReturnAMapContainingTheValuesOfAPGObject() throws Exception {
        JsonNode payload = objectMapper.valueToTree(
                Map.of("json_field_1", "json_value_1","json_field_2", "json_value_2"));

        Map<String, String> values = Map.of("json_field_1", "json_value_1", "json_field_2", "json_value_2");
        pGobject.setValue(objectMapper.writeValueAsString(values));

        Map<String, String> jsonValues = jsonToStringStringMapConverter.convertToEntityAttribute(pGobject);

        assertThat(objectMapper.writeValueAsString(jsonValues), is(payload.toString()));
    }

    @Test
    public void shouldReturnNoMapWhenDBFieldIsNull() throws Exception {
        pGobject.setValue(null);
        Map<String, String> jsonValues = jsonToStringStringMapConverter.convertToEntityAttribute(pGobject);

        assertThat(jsonValues, is(nullValue()));
    }

    @Test
    public void shouldReturnPGObjectContainingPredifinedValues() throws Exception {
        Map<String, String> values = Map.of("json_field_1", "json_value_1", "json_field_2", "json_value_2");
        pGobject = jsonToStringStringMapConverter.convertToDatabaseColumn(values);

        assertThat(pGobject.getType(), is("json"));
        assertThat(pGobject.getValue(), is(objectMapper.writeValueAsString(values)));
    }

    @Test
    public void shouldReturnNullWhenEmptyMap() {
        pGobject = jsonToStringStringMapConverter.convertToDatabaseColumn(Map.of());
        assertThat(pGobject.getValue(), is(nullValue()));
    }
}
