package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.pay.connector.common.model.api.jsonpatch.JsonPatchOp;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.google.common.collect.Lists.newArrayList;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static uk.gov.pay.connector.common.model.api.jsonpatch.JsonPatchKeys.FIELD_OPERATION;
import static uk.gov.pay.connector.common.model.api.jsonpatch.JsonPatchKeys.FIELD_OPERATION_PATH;
import static uk.gov.pay.connector.common.model.api.jsonpatch.JsonPatchKeys.FIELD_VALUE;

public class GatewayAccountPatchRequest {

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
        if (value != null && value.isTextual()) {
            return value.asText();
        }
        return null;
    }
    
    public Long valueAsLong() {
        if (value != null && value.isNumber()) {
            return new Long(value.asText());
        }
        return null;
    }

    public List<String> valueAsList() {
        if (value != null && value.isArray()) {
            return newArrayList(value.elements())
                    .stream()
                    .map(JsonNode::textValue)
                    .collect(toList());
        }
        return null;
    }

    public Map<String, String> valueAsObject() {
        if (value != null) {
            if ((value.isTextual() && !isEmpty(value.asText())) || (!value.isNull() && value.isObject())) {
                try {
                    return new ObjectMapper().readValue(value.traverse(), new TypeReference<Map<String, String>>() {});
                } catch (IOException e) {
                    throw new RuntimeException(format("Malformed JSON object in GatewayAccountRequest.value"), e);
                }
            }
        }
        return null;
    }


    private GatewayAccountPatchRequest(JsonPatchOp op, String path, JsonNode value) {
        this.op = op;
        this.path = path;
        this.value = value;
    }

    public static GatewayAccountPatchRequest from(JsonNode payload) {
        return new GatewayAccountPatchRequest(
                JsonPatchOp.valueOf(payload.get(FIELD_OPERATION).asText().toUpperCase()),
                payload.get(FIELD_OPERATION_PATH).asText(),
                payload.get(FIELD_VALUE));

    }
}
