package uk.gov.pay.connector.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;

import static uk.gov.pay.connector.model.domain.GatewayAccount.FIELD_OPERATION;
import static uk.gov.pay.connector.model.domain.GatewayAccount.FIELD_OPERATION_PATH;
import static uk.gov.pay.connector.model.domain.GatewayAccount.FIELD_VALUE;

public class NotifySettingsUpdateRequest {

    public static final String OP_REMOVE = "remove";
    public static final String OP_REPLACE = "replace";
    private String op;
    private String path;
    private Map<String, String> value;

    public NotifySettingsUpdateRequest(String op, String path, Map<String, String> value) {
        this.op = op;
        this.path = path;
        this.value = value;
    }

    public static NotifySettingsUpdateRequest from(JsonNode payload) {
        try {
            Map<String, String> value = payload.get(FIELD_VALUE) == null ? null
                    : new ObjectMapper().readValue(payload.get(FIELD_VALUE).traverse(),
                    new TypeReference<Map<String, String>>() {});
            String operation  = payload.get(FIELD_OPERATION).asText();
            String path = payload.get(FIELD_OPERATION_PATH).asText();

            return new NotifySettingsUpdateRequest(operation, path, value);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getOp() {
        return op;
    }

    public String getPath() {
        return path;
    }

    public Map<String, String> getValue() {
        return value;
    }
}
