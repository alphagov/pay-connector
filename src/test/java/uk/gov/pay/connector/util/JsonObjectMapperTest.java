package uk.gov.pay.connector.util;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import javax.ws.rs.WebApplicationException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class JsonObjectMapperTest {
    private JsonObjectMapper mapper = new JsonObjectMapper(new ObjectMapper());

    @Test
    public void shouldMapToObject() {
        String jsonString = "{\"id\":1}";
        LocalTestObject fromJson = mapper.getObject(jsonString, LocalTestObject.class);
        assertNotNull(fromJson);
        assertEquals(1, fromJson.getId());
    }

    @Test(expected = WebApplicationException.class)
    public void shouldThrowException() {
        String jsonString = "{\"id\":\"abc\"}";
        LocalTestObject fromJson = mapper.getObject(jsonString, LocalTestObject.class);
        assertNotNull(fromJson);
        assertEquals(1, fromJson.getId());
    }
}

class LocalTestObject {
    @JsonProperty("id")
    private int id;

    public int getId() {
        return id;
    }
}
