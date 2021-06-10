package uk.gov.pay.connector.common.validator;

import com.fasterxml.jackson.databind.JsonNode;
import uk.gov.pay.connector.common.exception.ValidationException;
import uk.gov.pay.connector.common.model.api.jsonpatch.JsonPatchOp;
import uk.gov.pay.connector.common.model.api.jsonpatch.JsonPatchRequest;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static uk.gov.pay.connector.common.model.api.jsonpatch.JsonPatchKeys.FIELD_OPERATION;
import static uk.gov.pay.connector.common.model.api.jsonpatch.JsonPatchKeys.FIELD_OPERATION_PATH;
import static uk.gov.pay.connector.common.model.api.jsonpatch.JsonPatchKeys.FIELD_VALUE;

public class JsonPatchRequestValidator {

    private static final Set<String> allowedOps = Arrays.stream(JsonPatchOp.values())
            .map(jsonPatchOp -> jsonPatchOp.name().toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());

    private final Map<PatchPathOperation, Consumer<JsonPatchRequest>> operationValidators;

    public JsonPatchRequestValidator(Map<PatchPathOperation, Consumer<JsonPatchRequest>> operationValidators) {
        this.operationValidators = operationValidators;
    }

    public void validate(JsonNode payload) {
        if (!payload.isArray()) {
            throw new ValidationException(Collections.singletonList("JSON is not an array"));
        }

        for (JsonNode jsonNode : payload) {
            List<String> fieldErrors = RequestValidator.checkIfExistsOrEmpty(jsonNode, FIELD_OPERATION, FIELD_OPERATION_PATH, FIELD_VALUE);
            if (!fieldErrors.isEmpty()) {
                throw new ValidationException(fieldErrors);
            }

            List<String> opPathTypeErrors = RequestValidator.checkIsString(jsonNode, FIELD_OPERATION, FIELD_OPERATION_PATH);
            if (!opPathTypeErrors.isEmpty()) {
                throw new ValidationException(opPathTypeErrors);
            }

            String op = jsonNode.get(FIELD_OPERATION).asText();
            List<String> opErrors = RequestValidator.checkIsAllowedValue(jsonNode, allowedOps, FIELD_OPERATION);
            if (!opErrors.isEmpty()) {
                throw new ValidationException(opErrors);
            }

            JsonPatchOp jsonPatchOp = JsonPatchOp.valueOf(op.toUpperCase());

            String path = jsonNode.get(FIELD_OPERATION_PATH).asText();
            Set<String> allowedPaths = operationValidators.keySet().stream().map(PatchPathOperation::getPath).collect(Collectors.toSet());

            List<String> pathErrors = RequestValidator.checkIsAllowedValue(jsonNode, allowedPaths, FIELD_OPERATION_PATH);
            if (!pathErrors.isEmpty()) {
                throw new ValidationException(pathErrors);
            }

            PatchPathOperation pathOperation = new PatchPathOperation(path, jsonPatchOp);
            if (!operationValidators.containsKey(pathOperation)) {
                throw new ValidationException(Collections.singletonList(format("Operation [%s] not supported for path [%s]", op, path)));
            }

            JsonPatchRequest request = JsonPatchRequest.from(jsonNode);
            operationValidators.get(pathOperation).accept(request);
        }
    }

    public static void throwIfValueNotBoolean(JsonPatchRequest request) {
        if (!request.valueIsBoolean()) {
            throw new ValidationException(Collections.singletonList(format("Value for path [%s] must be a boolean", request.getPath())));
        }
    }
}
