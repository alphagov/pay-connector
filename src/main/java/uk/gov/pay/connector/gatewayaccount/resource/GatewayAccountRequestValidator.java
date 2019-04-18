package uk.gov.pay.connector.gatewayaccount.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import uk.gov.pay.connector.common.exception.ValidationException;
import uk.gov.pay.connector.common.model.api.jsonpatch.JsonPatchOp;
import uk.gov.pay.connector.common.validator.RequestValidator;
import uk.gov.pay.connector.gatewayaccount.model.EmailCollectionMode;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.pay.connector.common.model.api.jsonpatch.JsonPatchKeys.FIELD_OPERATION;
import static uk.gov.pay.connector.common.model.api.jsonpatch.JsonPatchKeys.FIELD_OPERATION_PATH;
import static uk.gov.pay.connector.common.model.api.jsonpatch.JsonPatchKeys.FIELD_VALUE;
import static uk.gov.pay.connector.common.model.api.jsonpatch.JsonPatchOp.ADD;
import static uk.gov.pay.connector.common.model.api.jsonpatch.JsonPatchOp.REMOVE;
import static uk.gov.pay.connector.common.model.api.jsonpatch.JsonPatchOp.REPLACE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.FIELD_NOTIFY_API_TOKEN;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.FIELD_NOTIFY_PAYMENT_CONFIRMED_TEMPLATE_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.FIELD_NOTIFY_REFUND_ISSUED_TEMPLATE_ID;


public class GatewayAccountRequestValidator {

    public static final String CREDENTIALS_GATEWAY_MERCHANT_ID = "credentials/gateway_merchant_id";
    public static final String FIELD_ALLOW_APPLE_PAY = "allow_apple_pay";
    public static final String FIELD_ALLOW_GOOGLE_PAY = "allow_google_pay";
    public static final String FIELD_NOTIFY_SETTINGS = "notify_settings";
    public static final String FIELD_EMAIL_COLLECTION_MODE = "email_collection_mode";
    public static final String FIELD_CORPORATE_CREDIT_CARD_SURCHARGE_AMOUNT = "corporate_credit_card_surcharge_amount";
    public static final String FIELD_CORPORATE_DEBIT_CARD_SURCHARGE_AMOUNT = "corporate_debit_card_surcharge_amount";
    public static final String FIELD_CORPORATE_PREPAID_CREDIT_CARD_SURCHARGE_AMOUNT = "corporate_prepaid_credit_card_surcharge_amount";
    public static final String FIELD_CORPORATE_PREPAID_DEBIT_CARD_SURCHARGE_AMOUNT = "corporate_prepaid_debit_card_surcharge_amount";
    public static final String FIELD_ALLOW_ZERO_AMOUNT = "allow_zero_amount";
    
    private static final List<String> VALID_PATHS = asList(
            CREDENTIALS_GATEWAY_MERCHANT_ID,
            FIELD_NOTIFY_SETTINGS, 
            FIELD_EMAIL_COLLECTION_MODE, 
            FIELD_ALLOW_APPLE_PAY,
            FIELD_ALLOW_GOOGLE_PAY,
            FIELD_CORPORATE_CREDIT_CARD_SURCHARGE_AMOUNT,
            FIELD_CORPORATE_DEBIT_CARD_SURCHARGE_AMOUNT,
            FIELD_CORPORATE_PREPAID_CREDIT_CARD_SURCHARGE_AMOUNT,
            FIELD_CORPORATE_PREPAID_DEBIT_CARD_SURCHARGE_AMOUNT,
            FIELD_ALLOW_ZERO_AMOUNT);
    
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
            case CREDENTIALS_GATEWAY_MERCHANT_ID:
                validateGatewayMerchantId(payload, CREDENTIALS_GATEWAY_MERCHANT_ID);
                break;
            case FIELD_NOTIFY_SETTINGS: 
                validateNotifySettingsRequest(payload);
                break;
            case FIELD_EMAIL_COLLECTION_MODE:
                validateEmailCollectionMode(payload);
                break;
            case FIELD_ALLOW_GOOGLE_PAY:
                validateReplaceBooleanValue(payload);
                break;
            case FIELD_ALLOW_APPLE_PAY:
                validateReplaceBooleanValue(payload);
                break;
            case FIELD_ALLOW_ZERO_AMOUNT:
                validateReplaceBooleanValue(payload);
                break;
            case FIELD_CORPORATE_CREDIT_CARD_SURCHARGE_AMOUNT: 
            case FIELD_CORPORATE_DEBIT_CARD_SURCHARGE_AMOUNT:
            case FIELD_CORPORATE_PREPAID_CREDIT_CARD_SURCHARGE_AMOUNT:
            case FIELD_CORPORATE_PREPAID_DEBIT_CARD_SURCHARGE_AMOUNT:
                validateCorporateCardSurchargePayload(payload);
                break;
        }
    }

    private void validateGatewayMerchantId(JsonNode payload, String field) {
        throwIfInvalidFieldOperation(payload, ADD, REPLACE);
        throwIfNullFieldValue(payload.get(FIELD_VALUE));
        throwIfBlankFieldValue(payload.get(FIELD_VALUE));
        throwIfNotValidMerchantId(payload.get(FIELD_VALUE), field);
    }

    private void throwIfBlankFieldValue(JsonNode valueNode) {
        if (isBlank(valueNode.asText())) 
            throw new ValidationException(Collections.singletonList(format("Field [%s] cannot be empty", FIELD_VALUE)));
    }

    private void throwIfNotValidMerchantId(JsonNode valueNode, String field) {
        boolean isValidWorldpayMerchantId = valueNode.textValue().matches("[0-9a-f]{15}");
        if(!isValidWorldpayMerchantId) {
            throw new ValidationException(Collections.singletonList(format("Field [%s] value [%s] does not match that expected for a Worldpay Merchant ID; should be 15 characters and within range [0-9a-f]", field, valueNode.asText())));
        }
    }
    
    private void validateNotifySettingsRequest(JsonNode payload) {
        throwIfInvalidFieldOperation(payload, REPLACE, REMOVE);
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
        throwIfInvalidFieldOperation(payload, REPLACE);
        JsonNode valueNode = payload.get(FIELD_VALUE);
        throwIfNullFieldValue(valueNode);
        try {
            EmailCollectionMode.fromString(valueNode.asText());
        } catch (IllegalArgumentException e) {
            throw new ValidationException(Collections.singletonList(format("Value [%s] is not valid for [%s]", valueNode.asText(), FIELD_EMAIL_COLLECTION_MODE)));
        }
    }

    private void validateReplaceBooleanValue(JsonNode payload) {
        throwIfInvalidFieldOperation(payload, REPLACE);
        throwIfNullFieldValue(payload.get(FIELD_VALUE));
        throwIfNotBoolean(payload);
    }
    
    private void validateCorporateCardSurchargePayload(JsonNode payload) {
        throwIfInvalidFieldOperation(payload, REPLACE);
        throwIfNullFieldValue(payload.get(FIELD_VALUE));
        throwIfNotNumber(payload);
        throwIfNegativeNumber(payload);
    }

    private void throwIfNullFieldValue(JsonNode valueNode) {
        if (null == valueNode || valueNode.isNull()) 
            throw new ValidationException(Collections.singletonList(format("Field [%s] is required", FIELD_VALUE)));
    }

    private void throwIfInvalidFieldOperation(JsonNode payload, JsonPatchOp... allowedOps) {
        String path = payload.get(FIELD_OPERATION_PATH).asText();
        String op = payload.get(FIELD_OPERATION).asText();
        if (Arrays.stream(allowedOps).noneMatch(x -> x.name().toLowerCase().equals(op))) {
            throw new ValidationException(Collections.singletonList(format("Operation [%s] is not valid for path [%s]", op, path)));
        }
    }

    private void throwIfNotNumber(JsonNode payload) {
        if (!payload.get(FIELD_VALUE).isNumber()) {
            throw new ValidationException(Collections.singletonList(format("Value [%s] is not valid for path [%s]", payload.get(FIELD_VALUE).asText(), payload.get(FIELD_OPERATION_PATH).asText())));
        }
    }

    private void throwIfNotBoolean(JsonNode payload) {
        if (payload.get(FIELD_VALUE) != null && !payload.get(FIELD_VALUE).isBoolean()) {
            throw new ValidationException(Collections.singletonList(format("Value [%s] must be of type boolean for path [%s]", payload.get(FIELD_VALUE).asText(), payload.get(FIELD_OPERATION_PATH).asText())));
        }
    }
    
    private void throwIfNegativeNumber(JsonNode payload) {
        long amount = payload.get(FIELD_VALUE).longValue();
        if (amount < 0) {
            throw new ValidationException(Collections.singletonList(format("Value [%s] is not valid for path [%s]", amount, payload.get(FIELD_OPERATION_PATH).asText())));
        }
    }
}
