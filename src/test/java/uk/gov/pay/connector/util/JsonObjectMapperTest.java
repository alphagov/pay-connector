package uk.gov.pay.connector.util;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JsonObjectMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
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

    @Nested
    @DisplayName("ObjectToString")
    class TestObjectToString {

        ObjectMapper mockObjectMapper = mock(ObjectMapper.class);

        @Test
        void shouldConvertObjectToJsonStringSuccessfully() {
            LocalTestObject localTestObject = new LocalTestObject();
            localTestObject.setId(12345);

            String result = jsonObjectMapper.objectToString(localTestObject);

            assertEquals("{\"id\":12345}", result);
        }

        @Test
        void shouldThrowWebApplicationExceptionWhenSerializationFails() throws JsonProcessingException {
            LocalTestObject localTestObject = new LocalTestObject();
            when(mockObjectMapper.writeValueAsString(localTestObject))
                    .thenThrow(new JsonProcessingException("error-serialising-object") {
                    });

            jsonObjectMapper = new JsonObjectMapper(mockObjectMapper);

            assertThrows(WebApplicationException.class, () -> jsonObjectMapper.objectToString(localTestObject));
        }
    }
}

class LocalTestObject {
    @JsonProperty("id")
    private int id;

    int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
}
