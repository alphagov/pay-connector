package uk.gov.pay.connector.gatewayaccount.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import uk.gov.pay.connector.common.exception.ValidationException;
import uk.gov.pay.connector.common.model.api.jsonpatch.JsonPatchOp;
import uk.gov.pay.connector.common.validator.RequestValidator;
import uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetupTask;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static uk.gov.pay.connector.common.model.api.jsonpatch.JsonPatchKeys.FIELD_OPERATION;
import static uk.gov.pay.connector.common.model.api.jsonpatch.JsonPatchKeys.FIELD_OPERATION_PATH;
import static uk.gov.pay.connector.common.model.api.jsonpatch.JsonPatchKeys.FIELD_VALUE;

public class StripeAccountSetupRequestValidator {

    private final RequestValidator requestValidator;

    @Inject
    public StripeAccountSetupRequestValidator(RequestValidator requestValidator) {
        this.requestValidator = requestValidator;
    }

    public void validatePatchRequest(JsonNode payload) {
        if (!payload.isArray()) {
            throw new ValidationException(Collections.singletonList("JSON is not an array"));
        }
        
        for (JsonNode jsonNode : payload) {
            List<String> fieldErrors = requestValidator.checkIfExistsOrEmpty(jsonNode, FIELD_OPERATION, FIELD_OPERATION_PATH, FIELD_VALUE);
            if (!fieldErrors.isEmpty()) {
                throw new ValidationException(fieldErrors);
            }

            List<String> opPathTypeErrors = requestValidator.checkIsString(jsonNode, FIELD_OPERATION, FIELD_OPERATION_PATH);
            if (!opPathTypeErrors.isEmpty()) {
                throw new ValidationException(opPathTypeErrors);
            }

            Set<String> allowedPaths = Arrays.stream(StripeAccountSetupTask.values())
                    .map(stripeAccountSetupTask -> stripeAccountSetupTask.name().toLowerCase())
                    .collect(Collectors.toSet());

            String op = jsonNode.get(FIELD_OPERATION).asText();
            String path = jsonNode.get(FIELD_OPERATION_PATH).asText();

            List<String> pathErrors = requestValidator.checkIsAllowedValue(jsonNode, allowedPaths, FIELD_OPERATION_PATH);

            if (!pathErrors.isEmpty()) {
                throw new ValidationException(pathErrors);
            }

            List<String> valueErrors = requestValidator.checkIsBoolean(jsonNode, FIELD_VALUE);
            if (!valueErrors.isEmpty()) {
                throw new ValidationException(valueErrors);
            }

            if (!op.equals(JsonPatchOp.REPLACE.name().toLowerCase())) {
                throw new ValidationException(Collections.singletonList(format("Operation [%s] not supported for path [%s]", op, path)));
            }
        }
    }

}
