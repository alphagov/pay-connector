package uk.gov.pay.connector.gatewayaccount.resource;

import com.fasterxml.jackson.databind.JsonNode;
import uk.gov.pay.connector.common.exception.ValidationException;
import uk.gov.pay.connector.common.model.api.jsonpatch.JsonPatchOp;
import uk.gov.pay.connector.common.validator.PatchPathOperation;
import uk.gov.pay.connector.common.validator.PatchRequestValidator;
import uk.gov.pay.connector.common.validator.RequestValidator;
import uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetupTask;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static uk.gov.pay.connector.common.model.api.jsonpatch.JsonPatchKeys.FIELD_VALUE;

public class StripeAccountSetupRequestValidator {

    private static final Map<PatchPathOperation, Consumer<JsonNode>> operationValidators;

    static {
        Map<PatchPathOperation, Consumer<JsonNode>> map = new HashMap<>();
        Arrays.stream(StripeAccountSetupTask.values()).forEach(stripeAccountSetupTask ->
                map.put(
                        new PatchPathOperation(stripeAccountSetupTask.name().toLowerCase(), JsonPatchOp.REPLACE),
                        StripeAccountSetupRequestValidator::validateReplaceTaskOperation)
        );
        operationValidators = Collections.unmodifiableMap(map);
    }
    
    private final PatchRequestValidator validator = new PatchRequestValidator(operationValidators);

    public void validatePatchRequest(JsonNode payload) {
        validator.validate(payload);
    }

    private static void validateReplaceTaskOperation(JsonNode operation) {
        List<String> valueErrors = RequestValidator.checkIsBoolean(operation, FIELD_VALUE);
        if (!valueErrors.isEmpty()) {
            throw new ValidationException(valueErrors);
        }
    }

}
