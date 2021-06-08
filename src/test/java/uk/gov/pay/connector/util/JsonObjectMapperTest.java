package uk.gov.pay.connector.util;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import javax.ws.rs.WebApplicationException;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

public class JsonObjectMapperTest {
    private ObjectMapper objectMapper = new ObjectMapper();
    private JsonObjectMapper jsonObjectMapper = new JsonObjectMapper(objectMapper);

    @Test
    public void shouldMapToObject() {
        String jsonString = "{\"id\":1}";
        LocalTestObject fromJson = jsonObjectMapper.getObject(jsonString, LocalTestObject.class);
        assertNotNull(fromJson);
        assertEquals(1, fromJson.getId());
    }

    @Test()
    public void shouldThrowException() {
        String jsonString = "{\"id\":\"abc\"}";
        assertThrows(WebApplicationException.class, () -> jsonObjectMapper.getObject(jsonString, LocalTestObject.class));
    }
    
    @Test
    public void shouldMapToMap() throws JsonProcessingException {
        JsonNode jsonNode = objectMapper.readTree("{\"foo\":\"bar\"}");
        Map<String, String> fromJson = jsonObjectMapper.getAsMap(jsonNode);
        assertThat(fromJson, hasEntry("foo", "bar"));
    }
}

class LocalTestObject {
    @JsonProperty("id")
    private int id;

    public int getId() {
        return id;
    }
}
