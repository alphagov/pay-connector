package uk.gov.pay.connector.gateway.stripe.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class StripeExpandableFieldDeserializerTest {

    @Test
    void shouldDeserializeFieldWithTextualValue() throws Exception {
        String json = "{\"stripe_object\": \"an-id\"}";
        ObjectMapper objectMapper = new ObjectMapper();
        TestModel testModel = objectMapper.readValue(json, TestModel.class);
        
        assertThat(testModel.getStripeObject(), not(nullValue()));
        assertThat(testModel.getStripeObject().getId(), is("an-id"));
        assertThat(testModel.getStripeObject().isExpanded(), is(false));
        assertThat(testModel.getStripeObject().getExpanded(), is(Optional.empty()));
    }

    @Test
    void shouldDeserializeFieldWithExpandedValue() throws Exception {
        String json = "{\"stripe_object\": {\"id\": \"an-id\", \"foo\": \"bar\"}}";
        ObjectMapper objectMapper = new ObjectMapper();
        TestModel testModel = objectMapper.readValue(json, TestModel.class);

        assertThat(testModel.getStripeObject(), not(nullValue()));
        assertThat(testModel.getStripeObject().getId(), is("an-id"));
        assertThat(testModel.getStripeObject().isExpanded(), is(true));
        assertThat(testModel.getStripeObject().getExpanded().isPresent(), is(true));
        assertThat(testModel.getStripeObject().getExpanded().get().foo, is("bar"));
    }

    @Test
    void shouldThrowExceptionIfNestedObjectDoesNotHaveId() {
        String json = "{\"stripe_object\": {\"foo\": \"bar\"}}";
        ObjectMapper objectMapper = new ObjectMapper();
        JsonMappingException exception = assertThrows(JsonMappingException.class, () -> objectMapper.readValue(json, TestModel.class));
        assertThat(exception.getMessage(), startsWith("Expected field [id] to exist on nested object [stripe_object]"));
    }

    @Test
    void shouldDeserializeNullValueAsNull() throws Exception {
        String json = "{\"stripe_object\": null}";
        ObjectMapper objectMapper = new ObjectMapper();
        TestModel testModel = objectMapper.readValue(json, TestModel.class);
        
        assertThat(testModel.getStripeObject(), is(nullValue()));
    }
    
    @Test
    void shouldThrowExceptionIfFieldHasUnexpectedType() {
        String json = "{\"stripe_object\": 1}";
        ObjectMapper objectMapper = new ObjectMapper();
        JsonMappingException exception = assertThrows(JsonMappingException.class, () -> objectMapper.readValue(json, TestModel.class));
        assertThat(exception.getMessage(), startsWith("Field [stripe_object] is a non-object, non-textual type."));
    }

    private static class TestModel {
        @JsonProperty("stripe_object")
        @JsonDeserialize(using = StripeExpandableFieldDeserializer.class)
        private StripeExpandableField<TestStripeObject> stripeObject;

        public StripeExpandableField<TestStripeObject> getStripeObject() {
            return stripeObject;
        }
    }
    
    private static class TestStripeObject implements StripeObjectWithId {
        @JsonProperty("id")
        private String id;
        
        @JsonProperty("foo")
        private String foo;

        @Override
        public String getId() {
            return id;
        }
    }
}
