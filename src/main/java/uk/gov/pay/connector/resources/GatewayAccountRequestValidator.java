package uk.gov.pay.connector.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import uk.gov.pay.connector.util.Errors;
import uk.gov.pay.connector.validations.RequestValidator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static uk.gov.pay.connector.model.domain.GatewayAccount.FIELD_NOTIFY_API_TOKEN;
import static uk.gov.pay.connector.model.domain.GatewayAccount.FIELD_NOTIFY_TEMPLATE_ID;
import static uk.gov.pay.connector.model.domain.GatewayAccount.FIELD_OPERATION;
import static uk.gov.pay.connector.model.domain.GatewayAccount.FIELD_OPERATION_PATH;
import static uk.gov.pay.connector.model.domain.GatewayAccount.FIELD_VALUES;


public class GatewayAccountRequestValidator {

    private final RequestValidator requestValidator;
    private static final Map<String, List<String>> VALID_ATTRIBUTE_UPDATE_OPERATIONS = new HashMap<String, List<String>>() {{
        put(FIELD_OPERATION, asList("replace", "remove"));
    }};
    private static final String FIELD_NOTIFY_SETTINGS = "notify_settings";

    @Inject
    public GatewayAccountRequestValidator(RequestValidator requestValidator){
        this.requestValidator = requestValidator;
    }

    public Optional<Errors> validatePatchRequest(JsonNode payload){
        Optional<List<String>> pathCheck = requestValidator.checkIfExistsOrEmpty(payload,
                FIELD_OPERATION, FIELD_OPERATION_PATH);
        if(pathCheck.isPresent()){
            return pathCheck.map(Errors::from);
        }
        if(!payload.findValue(FIELD_OPERATION_PATH).asText().equals(FIELD_NOTIFY_SETTINGS)) {
            return Optional.of(Errors.from(format("Operation [%s] not supported for path [%s]",
                    FIELD_OPERATION,
                    payload.findValue(FIELD_OPERATION_PATH).asText())));
        }
        return validateNotifySettingsRequest(payload).map(Errors::from);

    }

    private Optional<List<String>> validateNotifySettingsRequest(JsonNode payload){
        String op = payload.get(FIELD_OPERATION).asText();
        if (!VALID_ATTRIBUTE_UPDATE_OPERATIONS.get(FIELD_OPERATION).contains(op)) {
            return Optional.of(asList(format("Operation [%s] is not valid for path [%s]", op, FIELD_OPERATION)));
        }
        if(op.equals("remove")) {
            return Optional.empty();
        }
        JsonNode valueNode = payload.get(FIELD_VALUES);
        if(null == valueNode) {
            return Optional.of(asList(format("Field [%s] is required", FIELD_VALUES)));
        }
        return requestValidator.checkIfExistsOrEmpty(valueNode, FIELD_NOTIFY_API_TOKEN, FIELD_NOTIFY_TEMPLATE_ID);
    }
}
