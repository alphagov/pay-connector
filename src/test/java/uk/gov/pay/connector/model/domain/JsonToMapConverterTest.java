package uk.gov.pay.connector.model.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;
import org.postgresql.util.PGobject;
import uk.gov.pay.connector.gatewayaccount.util.JsonToMapConverter;

import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

public class JsonToMapConverterTest {

    private JsonToMapConverter jsonToMapConverter;
    private PGobject pGobject;

    @Before
    public void setUp(){
        jsonToMapConverter = new JsonToMapConverter();
        pGobject = new PGobject();
        pGobject.setType("json");
    }

    @Test
    public void shouldReturnAMapContainingTheValuesOfAPGObject() throws Exception {
        JsonNode payload = new ObjectMapper()
                .valueToTree(ImmutableMap.of(
                        "json_field_1", "json_value_1",
                        "json_field_2", "json_value_2"));

        Map<String, String> values = ImmutableMap.of("json_field_1", "json_value_1", "json_field_2", "json_value_2");
        pGobject.setValue(new ObjectMapper().writeValueAsString(values));

        Map<String, String> jsonValues = jsonToMapConverter.convertToEntityAttribute(pGobject);

        assertThat(new ObjectMapper().writeValueAsString(jsonValues), is(payload.toString()));
    }

    @Test
    public void shouldReturnNoMapWhenDBFieldIsNull() throws Exception {
        pGobject.setValue(null);
        Map<String, String> jsonValues = jsonToMapConverter.convertToEntityAttribute(pGobject);

        assertThat(jsonValues, is(nullValue()));
    }

    @Test
    public void shouldReturnPGObjectContainingPredifinedValues() throws Exception {
        Map<String, String> values = ImmutableMap.of("json_field_1", "json_value_1", "json_field_2", "json_value_2");
        pGobject = jsonToMapConverter.convertToDatabaseColumn(values);

        assertThat(pGobject.getType(), is("json"));
        assertThat(pGobject.getValue(), is(new ObjectMapper().writeValueAsString(values)));
    }

    @Test
    public void shouldReturnNullWhenEmptyMap() throws Exception {
        Map<String, String> values = ImmutableMap.of();
        pGobject = jsonToMapConverter.convertToDatabaseColumn(values);
        assertThat(pGobject.getValue(), is(nullValue()));
    }
}
