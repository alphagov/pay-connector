package uk.gov.pay.connector.common.model.api.jsonpatch;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static uk.gov.pay.connector.common.model.api.jsonpatch.JsonPatchKeys.FIELD_OPERATION;
import static uk.gov.pay.connector.common.model.api.jsonpatch.JsonPatchKeys.FIELD_OPERATION_PATH;
import static uk.gov.pay.connector.common.model.api.jsonpatch.JsonPatchKeys.FIELD_VALUE;

public class JsonPatchRequest {

    private JsonPatchOp op;
    private String path;
    private JsonNode value;

    public JsonPatchOp getOp() {
        return op;
    }

    public String getPath() {
        return path;
    }

    public String valueAsString() {
        return value.asText();
    }
    
    public long valueAsLong() {
        if (value != null && value.isNumber()) {
            return Long.valueOf(value.asText());
        }
        throw new JsonNodeNotCorrectTypeException("JSON node " + value + " is not of type number");
    }
    
    public int valueAsInt() {
        if(value != null && value.isNumber()) {
            return Integer.valueOf(value.asText());
        }
        throw new JsonNodeNotCorrectTypeException("JSON node " + value + " is not of type number");
    }

    public boolean valueAsBoolean() {
        if (value != null && value.isBoolean()) {
            return Boolean.valueOf(value.asText());
        }
        throw new JsonNodeNotCorrectTypeException("JSON node " + value + " is not of type boolean");
    }

    public Map<String, String> valueAsObject() {
        if (value != null) {
            if ((value.isTextual() && !isEmpty(value.asText())) || (!value.isNull() && value.isObject())) {
                try {
                    return new ObjectMapper().readValue(value.traverse(), new TypeReference<Map<String, String>>() {});
                } catch (IOException e) {
                    throw new RuntimeException("Malformed JSON object in value", e);
                }
            }
        }
        return null;
    }


    private JsonPatchRequest(JsonPatchOp op, String path, JsonNode value) {
        this.op = op;
        this.path = path;
        this.value = value;
    }

    public static JsonPatchRequest from(JsonNode payload) {
        return new JsonPatchRequest(
                JsonPatchOp.valueOf(payload.get(FIELD_OPERATION).asText().toUpperCase()),
                payload.get(FIELD_OPERATION_PATH).asText(),
                payload.get(FIELD_VALUE));

    }
    
    public class JsonNodeNotCorrectTypeException extends RuntimeException {
        public JsonNodeNotCorrectTypeException(String message) {
            super(message);
        }
    }
}
