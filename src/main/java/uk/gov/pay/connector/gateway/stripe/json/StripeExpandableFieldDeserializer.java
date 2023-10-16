package uk.gov.pay.connector.gateway.stripe.json;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;

import java.io.IOException;

import static java.lang.String.format;

@Deprecated
public class StripeExpandableFieldDeserializer extends JsonDeserializer<StripeExpandableField<?>> implements ContextualDeserializer {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private JavaType type;
    private String name;

    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext context, BeanProperty property) {
        this.type = property.getType().containedType(0);
        this.name = property.getName();
        return this;
    }

    @Override
    public StripeExpandableField<?> deserialize(JsonParser jsonParser, DeserializationContext context) throws IOException, JacksonException {
        JsonNode jsonNode = context.readTree(jsonParser);
        if (jsonNode.isNull()) {
            return null;
        }
        if (jsonNode.isTextual()) {
            return new StripeExpandableField<>(jsonNode.asText(), null);
        } else if (jsonNode.isObject()) {
            if (!jsonNode.has("id")) {
                throw JsonMappingException.from(jsonParser, format("Expected field [id] to exist on nested object [%s]", name));
            }
            String id = jsonNode.get("id").asText();
            return new StripeExpandableField<>(id, objectMapper.treeToValue(jsonNode, type));
        }

        throw JsonMappingException.from(jsonParser, format("Field [%s] is a non-object, non-textual type.", name));
    }
}
