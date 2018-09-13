package uk.gov.pay.connector.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import uk.gov.pay.connector.exception.ValidationException;
import uk.gov.pay.connector.validations.RequestValidator;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static uk.gov.pay.connector.model.domain.GatewayAccount.FIELD_NOTIFY_API_TOKEN;
import static uk.gov.pay.connector.model.domain.GatewayAccount.FIELD_NOTIFY_TEMPLATE_ID;
import static uk.gov.pay.connector.model.domain.GatewayAccount.FIELD_OPERATION;
import static uk.gov.pay.connector.model.domain.GatewayAccount.FIELD_OPERATION_PATH;
import static uk.gov.pay.connector.model.domain.GatewayAccount.FIELD_VALUE;


public class GatewayAccountRequestValidator {

    private final RequestValidator requestValidator;
    private static final Map<String, List<String>> VALID_ATTRIBUTE_UPDATE_OPERATIONS = new HashMap<String, List<String>>() {{
        put(FIELD_OPERATION, asList("replace", "remove"));
    }};
    public static final String FIELD_NOTIFY_SETTINGS = "notify_settings";
    public static final String FIELD_EMAIL_COLLECTION_MODE = "email_collection_mode";
    private static final List<String> VALID_PATHS = Arrays.asList(FIELD_NOTIFY_SETTINGS, FIELD_EMAIL_COLLECTION_MODE);
    @Inject
    public GatewayAccountRequestValidator(RequestValidator requestValidator){
        this.requestValidator = requestValidator;
    }

    void validatePatchRequest(JsonNode payload){
        List<String> pathCheck = requestValidator.checkIfExistsOrEmpty(payload,
                FIELD_OPERATION, FIELD_OPERATION_PATH);
        if(!pathCheck.isEmpty()){
            throw new ValidationException(pathCheck);
        }
        String operation = payload.findValue(FIELD_OPERATION_PATH).asText();
        if(!VALID_PATHS.contains(operation)) {
            throw new ValidationException(Collections.singletonList(format("Operation [%s] not supported for path [%s]",
                    FIELD_OPERATION,
                    payload.findValue(FIELD_OPERATION_PATH).asText())));
        }
        if (operation.equalsIgnoreCase(FIELD_NOTIFY_SETTINGS)) {
            validateNotifySettingsRequest(payload);
        }
    }

    private void validateNotifySettingsRequest(JsonNode payload){
        String op = payload.get(FIELD_OPERATION).asText();
        if (!VALID_ATTRIBUTE_UPDATE_OPERATIONS.get(FIELD_OPERATION).contains(op)) {
            throw new ValidationException(Collections.singletonList(format("Operation [%s] is not valid for path [%s]", op, FIELD_OPERATION)));
        }
        if (!op.equalsIgnoreCase("remove")) {
            JsonNode valueNode = payload.get(FIELD_VALUE);
            if(null == valueNode) {
                throw new ValidationException(Collections.singletonList(format("Field [%s] is required", FIELD_VALUE)));
            }

            //todo PP-4111 add FIELD_NOTIFY_REFUND_ISSUED_TEMPLATE_ID when selfservice is merged
            List<String> missingMandatoryFields = requestValidator.checkIfExistsOrEmpty(valueNode, FIELD_NOTIFY_API_TOKEN, FIELD_NOTIFY_TEMPLATE_ID);
            if (!missingMandatoryFields.isEmpty()) {
                throw new ValidationException(missingMandatoryFields);
            }
        }
    }
}
