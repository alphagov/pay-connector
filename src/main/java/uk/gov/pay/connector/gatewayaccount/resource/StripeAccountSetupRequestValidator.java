package uk.gov.pay.connector.gatewayaccount.resource;

import com.fasterxml.jackson.databind.JsonNode;
import uk.gov.pay.connector.common.model.api.jsonpatch.JsonPatchOp;
import uk.gov.pay.connector.common.model.api.jsonpatch.JsonPatchRequest;
import uk.gov.pay.connector.common.validator.JsonPatchRequestValidator;
import uk.gov.pay.connector.common.validator.PatchPathOperation;
import uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetupTask;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class StripeAccountSetupRequestValidator {

    private static final Map<PatchPathOperation, Consumer<JsonPatchRequest>> operationValidators;

    static {
        Map<PatchPathOperation, Consumer<JsonPatchRequest>> map = new HashMap<>();
        Arrays.stream(StripeAccountSetupTask.values()).forEach(stripeAccountSetupTask ->
                map.put(
                        new PatchPathOperation(stripeAccountSetupTask.name().toLowerCase(), JsonPatchOp.REPLACE),
                        JsonPatchRequestValidator::throwIfValueNotBoolean)
        );
        operationValidators = Collections.unmodifiableMap(map);
    }
    
    private final JsonPatchRequestValidator validator = new JsonPatchRequestValidator(operationValidators);

    public void validatePatchRequest(JsonNode payload) {
        validator.validate(payload);
    }
}
