package uk.gov.pay.connector.gatewayaccount.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import uk.gov.pay.connector.common.exception.ValidationException;
import uk.gov.pay.connector.common.validator.RequestValidator;
import uk.gov.pay.connector.gatewayaccount.model.EmailCollectionMode;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.FIELD_NOTIFY_API_TOKEN;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.FIELD_NOTIFY_PAYMENT_CONFIRMED_TEMPLATE_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.FIELD_NOTIFY_REFUND_ISSUED_TEMPLATE_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.FIELD_OPERATION;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.FIELD_OPERATION_PATH;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.FIELD_VALUE;


public class GatewayAccountRequestValidator {

    private static final String REPLACE_OP = "replace";
    private static final String REMOVE_OP = "remove";
    public static final String FIELD_ALLOW_WEB_PAYMENTS = "allow_web_payments";
    public static final String FIELD_NOTIFY_SETTINGS = "notify_settings";
    public static final String FIELD_EMAIL_COLLECTION_MODE = "email_collection_mode";
    public static final String FIELD_CORPORATE_CREDIT_CARD_SURCHARGE_AMOUNT = "corporate_credit_card_surcharge_amount";
    public static final String FIELD_CORPORATE_DEBIT_CARD_SURCHARGE_AMOUNT = "corporate_debit_card_surcharge_amount";
    public static final String FIELD_CORPORATE_PREPAID_CREDIT_CARD_SURCHARGE_AMOUNT = "corporate_prepaid_credit_card_surcharge_amount";
    public static final String FIELD_CORPORATE_PREPAID_DEBIT_CARD_SURCHARGE_AMOUNT = "corporate_prepaid_debit_card_surcharge_amount";
    private static final List<String> VALID_PATHS = asList(FIELD_NOTIFY_SETTINGS, 
            FIELD_EMAIL_COLLECTION_MODE, 
            FIELD_ALLOW_WEB_PAYMENTS,
            FIELD_CORPORATE_CREDIT_CARD_SURCHARGE_AMOUNT,
            FIELD_CORPORATE_DEBIT_CARD_SURCHARGE_AMOUNT,
            FIELD_CORPORATE_PREPAID_CREDIT_CARD_SURCHARGE_AMOUNT,
            FIELD_CORPORATE_PREPAID_DEBIT_CARD_SURCHARGE_AMOUNT);
    
    private final RequestValidator requestValidator;
    
    @Inject
    public GatewayAccountRequestValidator(RequestValidator requestValidator){
        this.requestValidator = requestValidator;
    }

    public void validatePatchRequest(JsonNode payload){
        List<String> pathCheck = requestValidator.checkIfExistsOrEmpty(payload, FIELD_OPERATION, FIELD_OPERATION_PATH);
        if(!pathCheck.isEmpty()) 
            throw new ValidationException(pathCheck);
        
        String path = payload.findValue(FIELD_OPERATION_PATH).asText().toLowerCase();
        if(!VALID_PATHS.contains(path)) 
            throw new ValidationException(Collections.singletonList(format("Operation [%s] not supported for path [%s]", FIELD_OPERATION, path)));
        
        switch (path) {
            case FIELD_NOTIFY_SETTINGS: 
                validateNotifySettingsRequest(payload);
                break;
            case FIELD_EMAIL_COLLECTION_MODE:
                validateEmailCollectionMode(payload);
                break;
            case FIELD_ALLOW_WEB_PAYMENTS:
                validateAllowWebPayment(payload);
                break;
            case FIELD_CORPORATE_CREDIT_CARD_SURCHARGE_AMOUNT: 
            case FIELD_CORPORATE_DEBIT_CARD_SURCHARGE_AMOUNT:
            case FIELD_CORPORATE_PREPAID_CREDIT_CARD_SURCHARGE_AMOUNT:
            case FIELD_CORPORATE_PREPAID_DEBIT_CARD_SURCHARGE_AMOUNT:
                validateCorporateCardSurchargePayload(payload);
                break;
        }
    }

    private void validateNotifySettingsRequest(JsonNode payload) {
        throwIfInvalidFieldOperation(payload, REPLACE_OP, REMOVE_OP);
        String op = payload.get(FIELD_OPERATION).asText();
        if (!op.equalsIgnoreCase("remove")) {
            JsonNode valueNode = payload.get(FIELD_VALUE);
            throwIfNullFieldValue(valueNode);
            List<String> missingMandatoryFields = requestValidator.checkIfExistsOrEmpty(valueNode, FIELD_NOTIFY_API_TOKEN, FIELD_NOTIFY_PAYMENT_CONFIRMED_TEMPLATE_ID, FIELD_NOTIFY_REFUND_ISSUED_TEMPLATE_ID);
            if (!missingMandatoryFields.isEmpty()) {
                throw new ValidationException(missingMandatoryFields);
            }
        }
    }

    private void validateEmailCollectionMode(JsonNode payload) {
        throwIfInvalidFieldOperation(payload, REPLACE_OP);
        JsonNode valueNode = payload.get(FIELD_VALUE);
        throwIfNullFieldValue(valueNode);
        try {
            EmailCollectionMode.fromString(valueNode.asText());
        } catch (IllegalArgumentException e) {
            throw new ValidationException(Collections.singletonList(format("Value [%s] is not valid for [%s]", valueNode.asText(), FIELD_EMAIL_COLLECTION_MODE)));
        }
    }

    private void validateAllowWebPayment(JsonNode payload) {
        throwIfInvalidFieldOperation(payload, REPLACE_OP);
        throwIfNullFieldValue(payload.get(FIELD_VALUE));
        String booleanString = payload.get(FIELD_VALUE).asText().toLowerCase();
        if (!booleanString.equals("false") && !booleanString.equals("true")) {
            throw new ValidationException(Collections.singletonList(format("Value [%s] is not valid for [%s]", booleanString, FIELD_ALLOW_WEB_PAYMENTS)));
        }
    }
    
    private void validateCorporateCardSurchargePayload(JsonNode payload) {
        throwIfInvalidFieldOperation(payload, REPLACE_OP);
        throwIfNullFieldValue(payload.get(FIELD_VALUE));
        throwIfNotNumber(payload);
        throwIfNegativeNumber(payload);
    }

    private void throwIfNullFieldValue(JsonNode valueNode) {
        if (null == valueNode || valueNode.isNull()) 
            throw new ValidationException(Collections.singletonList(format("Field [%s] is required", FIELD_VALUE)));
    }

    private void throwIfInvalidFieldOperation(JsonNode payload, String... allowedOps) {
        String path = payload.get(FIELD_OPERATION_PATH).asText();
        String op = payload.get(FIELD_OPERATION).asText();
        if (Arrays.stream(allowedOps).noneMatch(x -> x.equalsIgnoreCase(op))) {
            throw new ValidationException(Collections.singletonList(format("Operation [%s] is not valid for path [%s]", op, path)));
        }
    }

    private void throwIfNotNumber(JsonNode payload) {
        if (!payload.get(FIELD_VALUE).isNumber()) {
            throw new ValidationException(Collections.singletonList(format("Value [%s] is not valid for path [%s]", payload.get(FIELD_VALUE).asText(), payload.get(FIELD_OPERATION_PATH).asText())));
        }
    }
    
    private void throwIfNegativeNumber(JsonNode payload) {
        long amount = payload.get(FIELD_VALUE).longValue();
        if (amount < 0) {
            throw new ValidationException(Collections.singletonList(format("Value [%s] is not valid for path [%s]", amount, payload.get(FIELD_OPERATION_PATH).asText())));
        }
    }
}
