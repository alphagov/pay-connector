package uk.gov.pay.connector.gatewayaccount.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import uk.gov.pay.connector.common.validator.RequestValidator;
import uk.gov.pay.connector.gatewayaccount.model.EmailCollectionMode;
import uk.gov.service.payments.commons.api.exception.ValidationException;
import uk.gov.service.payments.commons.model.jsonpatch.JsonPatchOp;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static java.lang.String.format;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.FIELD_NOTIFY_API_TOKEN;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.FIELD_NOTIFY_PAYMENT_CONFIRMED_TEMPLATE_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.FIELD_NOTIFY_REFUND_ISSUED_TEMPLATE_ID;
import static uk.gov.service.payments.commons.model.jsonpatch.JsonPatchKeys.FIELD_OPERATION;
import static uk.gov.service.payments.commons.model.jsonpatch.JsonPatchKeys.FIELD_OPERATION_PATH;
import static uk.gov.service.payments.commons.model.jsonpatch.JsonPatchKeys.FIELD_VALUE;
import static uk.gov.service.payments.commons.model.jsonpatch.JsonPatchOp.REMOVE;
import static uk.gov.service.payments.commons.model.jsonpatch.JsonPatchOp.REPLACE;


public class GatewayAccountRequestValidator {

    public static final String FIELD_ALLOW_APPLE_PAY = "allow_apple_pay";
    public static final String FIELD_ALLOW_GOOGLE_PAY = "allow_google_pay";
    public static final String FIELD_NOTIFY_SETTINGS = "notify_settings";
    public static final String FIELD_EMAIL_COLLECTION_MODE = "email_collection_mode";
    public static final String FIELD_CORPORATE_CREDIT_CARD_SURCHARGE_AMOUNT = "corporate_credit_card_surcharge_amount";
    public static final String FIELD_CORPORATE_DEBIT_CARD_SURCHARGE_AMOUNT = "corporate_debit_card_surcharge_amount";
    public static final String FIELD_CORPORATE_PREPAID_CREDIT_CARD_SURCHARGE_AMOUNT = "corporate_prepaid_credit_card_surcharge_amount";
    public static final String FIELD_CORPORATE_PREPAID_DEBIT_CARD_SURCHARGE_AMOUNT = "corporate_prepaid_debit_card_surcharge_amount";
    public static final String FIELD_ALLOW_ZERO_AMOUNT = "allow_zero_amount";
    public static final String FIELD_INTEGRATION_VERSION_3DS = "integration_version_3ds";
    public static final String FIELD_BLOCK_PREPAID_CARDS = "block_prepaid_cards";
    public static final String FIELD_ALLOW_MOTO = "allow_moto";
    public static final String FIELD_MOTO_MASK_CARD_NUMBER_INPUT = "moto_mask_card_number_input";
    public static final String FIELD_MOTO_MASK_CARD_SECURITY_CODE_INPUT = "moto_mask_card_security_code_input";
    public static final String FIELD_ALLOW_TELEPHONE_PAYMENT_NOTIFICATIONS = "allow_telephone_payment_notifications";
    public static final String FIELD_WORLDPAY_CORPORATE_EXEMPTIONS_ENABLED = "worldpay_corporate_exemptions_enabled";
    public static final String FIELD_WORLDPAY_EXEMPTION_ENGINE_ENABLED = "worldpay_exemption_engine_enabled";
    public static final String FIELD_SEND_PAYER_IP_ADDRESS_TO_GATEWAY = "send_payer_ip_address_to_gateway";
    public static final String FIELD_SEND_PAYER_EMAIL_TO_GATEWAY = "send_payer_email_to_gateway";
    public static final String FIELD_PROVIDER_SWITCH_ENABLED = "provider_switch_enabled";
    public static final String FIELD_SEND_REFERENCE_TO_GATEWAY = "send_reference_to_gateway";
    public static final String FIELD_ALLOW_AUTHORISATION_API = "allow_authorisation_api";
    public static final String FIELD_RECURRING_ENABLED = "recurring_enabled";
    public static final String FIELD_DISABLED = "disabled";
    public static final String FIELD_DISABLED_REASON = "disabled_reason";

    private static final Set<String> VALID_PATHS = Set.of(
            FIELD_NOTIFY_SETTINGS,
            FIELD_EMAIL_COLLECTION_MODE,
            FIELD_ALLOW_APPLE_PAY,
            FIELD_ALLOW_GOOGLE_PAY,
            FIELD_CORPORATE_CREDIT_CARD_SURCHARGE_AMOUNT,
            FIELD_CORPORATE_DEBIT_CARD_SURCHARGE_AMOUNT,
            FIELD_CORPORATE_PREPAID_CREDIT_CARD_SURCHARGE_AMOUNT,
            FIELD_CORPORATE_PREPAID_DEBIT_CARD_SURCHARGE_AMOUNT,
            FIELD_ALLOW_ZERO_AMOUNT,
            FIELD_INTEGRATION_VERSION_3DS,
            FIELD_BLOCK_PREPAID_CARDS,
            FIELD_ALLOW_MOTO,
            FIELD_MOTO_MASK_CARD_NUMBER_INPUT,
            FIELD_MOTO_MASK_CARD_SECURITY_CODE_INPUT,
            FIELD_ALLOW_TELEPHONE_PAYMENT_NOTIFICATIONS,
            FIELD_WORLDPAY_CORPORATE_EXEMPTIONS_ENABLED,
            FIELD_WORLDPAY_EXEMPTION_ENGINE_ENABLED,
            FIELD_SEND_PAYER_IP_ADDRESS_TO_GATEWAY,
            FIELD_SEND_PAYER_EMAIL_TO_GATEWAY,
            FIELD_PROVIDER_SWITCH_ENABLED,
            FIELD_SEND_REFERENCE_TO_GATEWAY,
            FIELD_ALLOW_AUTHORISATION_API,
            FIELD_RECURRING_ENABLED,
            FIELD_DISABLED,
            FIELD_DISABLED_REASON);

    private final RequestValidator requestValidator;

    @Inject
    public GatewayAccountRequestValidator(RequestValidator requestValidator) {
        this.requestValidator = requestValidator;
    }

    public void validatePatchRequest(JsonNode payload) {
        List<String> pathCheck = requestValidator.checkIfExistsOrEmpty(payload, FIELD_OPERATION, FIELD_OPERATION_PATH);
        if (!pathCheck.isEmpty())
            throw new ValidationException(pathCheck);

        String path = payload.findValue(FIELD_OPERATION_PATH).asText().toLowerCase();
        if (!VALID_PATHS.contains(path))
            throw new ValidationException(Collections.singletonList(format("Operation [%s] not supported for path [%s]", FIELD_OPERATION, path)));

        switch (path) {
            case FIELD_NOTIFY_SETTINGS:
                validateNotifySettingsRequest(payload);
                break;
            case FIELD_EMAIL_COLLECTION_MODE:
                validateEmailCollectionMode(payload);
                break;
            case FIELD_ALLOW_GOOGLE_PAY:
            case FIELD_BLOCK_PREPAID_CARDS:
            case FIELD_ALLOW_ZERO_AMOUNT:
            case FIELD_ALLOW_APPLE_PAY:
            case FIELD_ALLOW_MOTO:
            case FIELD_MOTO_MASK_CARD_NUMBER_INPUT:
            case FIELD_MOTO_MASK_CARD_SECURITY_CODE_INPUT:
            case FIELD_ALLOW_TELEPHONE_PAYMENT_NOTIFICATIONS:
            case FIELD_WORLDPAY_CORPORATE_EXEMPTIONS_ENABLED:
            case FIELD_WORLDPAY_EXEMPTION_ENGINE_ENABLED:
            case FIELD_SEND_PAYER_IP_ADDRESS_TO_GATEWAY:
            case FIELD_SEND_PAYER_EMAIL_TO_GATEWAY:
            case FIELD_PROVIDER_SWITCH_ENABLED:
            case FIELD_SEND_REFERENCE_TO_GATEWAY:
            case FIELD_ALLOW_AUTHORISATION_API:
            case FIELD_RECURRING_ENABLED:
            case FIELD_DISABLED:
                validateReplaceBooleanValue(payload);
                break;
            case FIELD_CORPORATE_CREDIT_CARD_SURCHARGE_AMOUNT:
            case FIELD_CORPORATE_DEBIT_CARD_SURCHARGE_AMOUNT:
            case FIELD_CORPORATE_PREPAID_CREDIT_CARD_SURCHARGE_AMOUNT:
            case FIELD_CORPORATE_PREPAID_DEBIT_CARD_SURCHARGE_AMOUNT:
                validateCorporateCardSurchargePayload(payload);
                break;
            case FIELD_INTEGRATION_VERSION_3DS:
                validateIntegrationVersion3ds(payload);
                break;
            case FIELD_DISABLED_REASON:
                validateReplaceString(payload);
                break;
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
    
    private void validateReplaceString(JsonNode payload) {
        throwIfInvalidFieldOperation(payload, REPLACE);
        throwIfNotString(payload);
    }

    private void validateCorporateCardSurchargePayload(JsonNode payload) {
        throwIfInvalidFieldOperation(payload, REPLACE);
        throwIfNullFieldValue(payload.get(FIELD_VALUE));
        throwIfNotNumber(payload);
        throwIfNegativeNumber(payload);
    }

    private void validateIntegrationVersion3ds(JsonNode payload) {
        throwIfInvalidFieldOperation(payload, REPLACE);
        throwIfNotNumber(payload);
        throwIfNotInRange(payload, 1, 2);
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
    
    private void throwIfNotString(JsonNode payload) {
        if (payload.get(FIELD_VALUE) != null && !payload.get(FIELD_VALUE).isTextual()) {
            throw new ValidationException(Collections.singletonList(format("Value [%s] must be a string for path [%s]", payload.get(FIELD_VALUE).asText(), payload.get(FIELD_OPERATION_PATH).asText())));
        }
    }

    private void throwIfNegativeNumber(JsonNode payload) {
        long value = payload.get(FIELD_VALUE).longValue();
        if (value < 0) {
            throw new ValidationException(Collections.singletonList(format("Value [%s] is not valid for path [%s]", value, payload.get(FIELD_OPERATION_PATH).asText())));
        }
    }

    private void throwIfNotInRange(JsonNode payload, int min, int max) {
        int value = payload.get(FIELD_VALUE).intValue();
        if (value < min || value > max) {
            throw new ValidationException(Collections.singletonList(format("Value [%s] is not valid for path [%s]", value, payload.get(FIELD_OPERATION_PATH).asText())));
        }
    }
}
