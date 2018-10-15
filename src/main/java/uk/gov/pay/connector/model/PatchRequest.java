package uk.gov.pay.connector.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.google.common.collect.Lists.newArrayList;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.FIELD_OPERATION;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.FIELD_OPERATION_PATH;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.FIELD_VALUE;

public class PatchRequest {

    private String op;
    private String path;
    private JsonNode value;

    public String getOp() {
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


    private PatchRequest(String op, String path, JsonNode value) {
        this.op = op;
        this.path = path;
        this.value = value;
    }

    public static PatchRequest from(JsonNode payload) {
        return new PatchRequest(
                payload.get(FIELD_OPERATION).asText(),
                payload.get(FIELD_OPERATION_PATH).asText(),
                payload.get(FIELD_VALUE));

    }
}
