package uk.gov.pay.connector.util;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.WebApplicationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonObjectMapperTest {
    private ObjectMapper objectMapper = new ObjectMapper();
    private JsonObjectMapper jsonObjectMapper = new JsonObjectMapper(objectMapper);

    @Test
    void shouldMapToObject() {
        String jsonString = "{\"id\":1}";
        LocalTestObject fromJson = jsonObjectMapper.getObject(jsonString, LocalTestObject.class);
        assertNotNull(fromJson);
        assertEquals(1, fromJson.getId());
    }

    @Test
    void shouldThrowException() {
        String jsonString = "{\"id\":\"abc\"}";
        assertThrows(WebApplicationException.class, () -> jsonObjectMapper.getObject(jsonString, LocalTestObject.class));
    }
}

class LocalTestObject {
    @JsonProperty("id")
    private int id;

    int getId() {
        return id;
    }
}
