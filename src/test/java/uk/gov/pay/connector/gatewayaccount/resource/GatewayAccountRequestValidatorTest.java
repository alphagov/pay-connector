package uk.gov.pay.connector.gatewayaccount.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.gov.pay.connector.common.validator.RequestValidator;
import uk.gov.service.payments.commons.api.exception.ValidationException;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.FIELD_NOTIFY_API_TOKEN;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.FIELD_NOTIFY_PAYMENT_CONFIRMED_TEMPLATE_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.FIELD_NOTIFY_REFUND_ISSUED_TEMPLATE_ID;
import static uk.gov.service.payments.commons.model.jsonpatch.JsonPatchKeys.FIELD_OPERATION;
import static uk.gov.service.payments.commons.model.jsonpatch.JsonPatchKeys.FIELD_OPERATION_PATH;
import static uk.gov.service.payments.commons.model.jsonpatch.JsonPatchKeys.FIELD_VALUE;


class GatewayAccountRequestValidatorTest {

    private GatewayAccountRequestValidator validator;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void before() {
        validator = new GatewayAccountRequestValidator(new RequestValidator());
    }

    @ParameterizedTest
    @MethodSource("provider")
    void shouldThrowWhenRequestsAreInvalid(String op, String path, Object value, String expectedErrorMessage) {
        Map<String, Object> patch = new HashMap<>() {{
            put(FIELD_OPERATION, op);
            put(FIELD_OPERATION_PATH, path);
        }};

        if (value != null) patch.put(FIELD_VALUE, value);

        JsonNode jsonNode = objectMapper.valueToTree(patch);
        var validationException = assertThrows(ValidationException.class,
                () -> validator.validatePatchRequest(jsonNode));

        assertThat(validationException.getErrors().size(), is(1));
        assertThat(validationException.getErrors(), hasItems(expectedErrorMessage));

    }

    @Test
    void shouldThrow_whenFieldsAreMissing() {
        JsonNode jsonNode = objectMapper.valueToTree(
                Map.of(FIELD_OPERATION, "replace", FIELD_OPERATION_PATH, "notify_settings"));

        var validationException = assertThrows(ValidationException.class,
                () -> validator.validatePatchRequest(jsonNode));

        assertThat(validationException.getErrors().size(), is(1));
        assertThat(validationException.getErrors(), hasItems("Field [value] is required"));
    }

    @Test
    void shouldThrow_whenFieldsNotValidInValue() {
        JsonNode jsonNode = objectMapper.valueToTree(
                Map.of(FIELD_OPERATION, "replace",
                        FIELD_OPERATION_PATH, "notify_settings",
                        FIELD_VALUE, Map.of("timbuktu", "anapitoken",
                                "colombo", "atemplateid")));

        var validationException = assertThrows(ValidationException.class,
                () -> validator.validatePatchRequest(jsonNode));

        assertThat(validationException.getErrors().size(), is(3));
        assertThat(validationException.getErrors(), hasItems(
                "Field [api_token] is required",
                "Field [template_id] is required",
                "Field [refund_issued_template_id] is required"));
    }

    @Test
    void shouldThrow_whenInvalidPathOnOperation() {
        JsonNode jsonNode = objectMapper.valueToTree(Map.of(FIELD_OPERATION, "replace",
                FIELD_OPERATION_PATH, "service_name"));

        var validationException = assertThrows(ValidationException.class,
                () -> validator.validatePatchRequest(jsonNode));

        assertThat(validationException.getErrors().size(), is(1));
        assertThat(validationException.getErrors(), hasItems("Operation [op] not supported for path [service_name]"));
    }

    @Test
    void shouldThrow_whenEmailCollectionModeIsInvalid() {
        JsonNode jsonNode = objectMapper.valueToTree(
                Map.of(FIELD_OPERATION, "replace",
                        FIELD_OPERATION_PATH, "email_collection_mode",
                        FIELD_VALUE, "someValue"));

        var validationException = assertThrows(ValidationException.class,
                () -> validator.validatePatchRequest(jsonNode));

        assertThat(validationException.getErrors().size(), is(1));
        assertThat(validationException.getErrors(), hasItems("Value [someValue] is not valid for [email_collection_mode]"));
    }

    @Test
    void shouldNotThrow_whenAllValidationsPassed() {
        JsonNode jsonNode = objectMapper.valueToTree(
                Map.of(FIELD_OPERATION, "replace",
                        FIELD_OPERATION_PATH, "notify_settings",
                        FIELD_VALUE, Map.of(
                                FIELD_NOTIFY_API_TOKEN, "anapitoken",
                                FIELD_NOTIFY_PAYMENT_CONFIRMED_TEMPLATE_ID, "atemplateid",
                                FIELD_NOTIFY_REFUND_ISSUED_TEMPLATE_ID, "anothertemplateid")));

        validator.validatePatchRequest(jsonNode);
    }

    @Test
    public void shouldNotThrow_whenPathIsValidEmailCollectionMode() {
        JsonNode jsonNode = objectMapper.valueToTree(
                Map.of(FIELD_OPERATION, "replace", 
                        FIELD_OPERATION_PATH, "email_collection_mode",
                        FIELD_VALUE, "MANDATORY"));
        validator.validatePatchRequest(jsonNode);
    }

    @Test
    void shouldThrow_whenInvalidOperation() {
        JsonNode jsonNode = objectMapper.valueToTree(
                Map.of(FIELD_OPERATION, "delete",
                        FIELD_OPERATION_PATH, "notify_settings",
                        FIELD_VALUE, Map.of(FIELD_NOTIFY_API_TOKEN, "anapitoken", 
                                FIELD_NOTIFY_PAYMENT_CONFIRMED_TEMPLATE_ID, "atemplateid")));

        var validationException = assertThrows(ValidationException.class,
                () -> validator.validatePatchRequest(jsonNode));

        assertThat(validationException.getErrors().size(), is(1));
        assertThat(validationException.getErrors(), hasItems("Operation [delete] is not valid for path [notify_settings]"));
    }

    @Test
    void shouldIgnoreEmptyOrMissingValue_whenRemoveOperation() {
        JsonNode jsonNode = objectMapper.valueToTree(
                Map.of(FIELD_OPERATION, "remove",
                        FIELD_OPERATION_PATH, "notify_settings",
                        FIELD_VALUE, Map.of(FIELD_NOTIFY_API_TOKEN, "")));
        validator.validatePatchRequest(jsonNode);
    }

    @Test
    public void shouldThrow_whenCorporateCreditSurchargeAmountIsInvalid() {
        JsonNode jsonNode = objectMapper.valueToTree(
                Map.of(FIELD_OPERATION, "replace",
                        FIELD_OPERATION_PATH, "corporate_credit_card_surcharge_amount",
                        FIELD_VALUE, -100));
        
        var validationException = assertThrows(ValidationException.class,
                () -> validator.validatePatchRequest(jsonNode));

        assertThat(validationException.getErrors().size(), is(1));
        assertThat(validationException.getErrors(), hasItems("Value [-100] is not valid for path [corporate_credit_card_surcharge_amount]"));
    }

    @Test
    void shouldThrow_whenIncorrectOperationForCorporateDebitSurchargeAmount() {
        JsonNode jsonNode = objectMapper.valueToTree(
                Map.of(FIELD_OPERATION, "remove",
                        FIELD_OPERATION_PATH, "corporate_debit_card_surcharge_amount",
                        FIELD_VALUE, 250));

        var validationException = assertThrows(ValidationException.class,
                () -> validator.validatePatchRequest(jsonNode));

        assertThat(validationException.getErrors().size(), is(1));
        assertThat(validationException.getErrors(), hasItems(
                "Operation [remove] is not valid for path [corporate_debit_card_surcharge_amount]"));
    }

    @Test
    void shouldThrow_whenMissingValueForCorporatePrepaidDebitSurchargeAmount() {
        JsonNode jsonNode = objectMapper.valueToTree(
                Map.of(FIELD_OPERATION, "replace", FIELD_OPERATION_PATH, "corporate_prepaid_debit_card_surcharge_amount"));

        var validationException = assertThrows(ValidationException.class,
                () -> validator.validatePatchRequest(jsonNode));

        assertThat(validationException.getErrors().size(), is(1));
        assertThat(validationException.getErrors(), hasItems("Field [value] is required"));
    }

    @Test
    public void shouldThrow_whenNullValueForCorporatePrepaidDebitSurchargeAmount() {
        Map<String, Object> valueMap = new HashMap<>();
        valueMap.put("op", "replace");
        valueMap.put("path", "corporate_prepaid_debit_card_surcharge_amount");
        valueMap.put("value", null);
        JsonNode jsonNode = objectMapper.valueToTree(valueMap);

        var validationException = assertThrows(ValidationException.class,
                () -> validator.validatePatchRequest(jsonNode));

        assertThat(validationException.getErrors().size(), is(1));
        assertThat(validationException.getErrors(), hasItems("Field [value] is required"));
    }

    @Test
    void shouldThrow_whenEmptyValueForCorporatePrepaidDebitSurchargeAmount() {
        JsonNode jsonNode = objectMapper.valueToTree(
                Map.of(FIELD_OPERATION, "replace",
                        FIELD_OPERATION_PATH, "corporate_prepaid_debit_card_surcharge_amount",
                        FIELD_VALUE, ""));

        var validationException = assertThrows(ValidationException.class,
                () -> validator.validatePatchRequest(jsonNode));

        assertThat(validationException.getErrors().size(), is(1));
        assertThat(validationException.getErrors(), hasItems("Value [] is not valid for path [corporate_prepaid_debit_card_surcharge_amount]"));
    }

    @Test
    void shouldThrow_whenNotBooleanValueForSendPayerEmailToGateway() {
        JsonNode jsonNode = objectMapper.valueToTree(
                Map.of(FIELD_OPERATION, "replace",
                        FIELD_OPERATION_PATH, "send_payer_email_to_gateway",
                        FIELD_VALUE, "true"));

        var validationException = assertThrows(ValidationException.class,
                () -> validator.validatePatchRequest(jsonNode));

        assertThat(validationException.getErrors().size(), is(1));
        assertThat(validationException.getErrors(), hasItems("Value [true] must be of type boolean for path [send_payer_email_to_gateway]"));
    }

    @Test
    void shouldThrow_whenNotBooleanValueForSendPayerIPAddressToGateway() {
        JsonNode jsonNode = objectMapper.valueToTree(
                Map.of(FIELD_OPERATION, "replace",
                        FIELD_OPERATION_PATH, "send_payer_ip_address_to_gateway",
                        FIELD_VALUE, "true"));

        var validationException = assertThrows(ValidationException.class,
                () -> validator.validatePatchRequest(jsonNode));

        assertThat(validationException.getErrors().size(), is(1));
        assertThat(validationException.getErrors(), hasItems("Value [true] must be of type boolean for path [send_payer_ip_address_to_gateway]"));
    }

    static Stream<Arguments> provider() {
        return Stream.of(
                arguments("bad", "allow_apple_pay", "true", "Operation [bad] is not valid for path [allow_apple_pay]"),
                arguments("bad", "allow_google_pay", "true", "Operation [bad] is not valid for path [allow_google_pay]"),
                arguments("replace", "allow_apple_pay", null, "Field [value] is required"),
                arguments("replace", "allow_google_pay", null, "Field [value] is required"),
                arguments("replace", "allow_apple_pay", "unfalse", "Value [unfalse] must be of type boolean for path [allow_apple_pay]"),
                arguments("replace", "allow_google_pay", "unfalse", "Value [unfalse] must be of type boolean for path [allow_google_pay]"),
                arguments("bad", "allow_zero_amount", "true", "Operation [bad] is not valid for path [allow_zero_amount]"),
                arguments("replace", "allow_zero_amount", null, "Field [value] is required"),
                arguments("replace", "allow_zero_amount", "unfalse", "Value [unfalse] must be of type boolean for path [allow_zero_amount]"),
                arguments("add", "integration_version_3ds", "1", "Operation [add] is not valid for path [integration_version_3ds]"),
                arguments("replace", "integration_version_3ds", "a-string", "Value [a-string] is not valid for path [integration_version_3ds]"),
                arguments("replace", "integration_version_3ds", "0", "Value [0] is not valid for path [integration_version_3ds]"),
                arguments("replace", "integration_version_3ds", "3", "Value [3] is not valid for path [integration_version_3ds]"),
                arguments("add", "block_prepaid_cards", "true", "Operation [add] is not valid for path [block_prepaid_cards]"),
                arguments("remove", "block_prepaid_cards", "true", "Operation [remove] is not valid for path [block_prepaid_cards]"),
                arguments("bad", "block_prepaid_cards", "true", "Operation [bad] is not valid for path [block_prepaid_cards]"),
                arguments("replace", "block_prepaid_cards", null, "Field [value] is required"),
                arguments("replace", "block_prepaid_cards", "unfalse", "Value [unfalse] must be of type boolean for path [block_prepaid_cards]"),
                arguments("bad", "allow_moto", "true", "Operation [bad] is not valid for path [allow_moto]"),
                arguments("replace", "allow_moto", null, "Field [value] is required"),
                arguments("replace", "allow_moto", "unfalse", "Value [unfalse] must be of type boolean for path [allow_moto]"),
                arguments("bad", "moto_mask_card_number_input", "true", "Operation [bad] is not valid for path [moto_mask_card_number_input]"),
                arguments("replace", "moto_mask_card_number_input", null, "Field [value] is required"),
                arguments("replace", "moto_mask_card_number_input", "unfalse", "Value [unfalse] must be of type boolean for path [moto_mask_card_number_input]"),
                arguments("bad", "moto_mask_card_security_code_input", "true", "Operation [bad] is not valid for path [moto_mask_card_security_code_input]"),
                arguments("replace", "moto_mask_card_security_code_input", null, "Field [value] is required"),
                arguments("replace", "moto_mask_card_security_code_input", "unfalse", "Value [unfalse] must be of type boolean for path [moto_mask_card_security_code_input]"),
                arguments("replace", "allow_telephone_payment_notifications", null, "Field [value] is required"),
                arguments("replace", "allow_telephone_payment_notifications", "unfalse", "Value [unfalse] must be of type boolean for path [allow_telephone_payment_notifications]"),
                arguments("replace", "worldpay_exemption_engine_enabled", null, "Field [value] is required"),
                arguments("replace", "worldpay_exemption_engine_enabled", "unfalse", "Value [unfalse] must be of type boolean for path [worldpay_exemption_engine_enabled]"),
                arguments("replace", "send_payer_ip_address_to_gateway", null, "Field [value] is required"),
                arguments("replace", "send_payer_ip_address_to_gateway", "unfalse", "Value [unfalse] must be of type boolean for path [send_payer_ip_address_to_gateway]"),
                arguments("replace", "send_payer_email_to_gateway", null, "Field [value] is required"),
                arguments("replace", "send_payer_email_to_gateway", "unfalse", "Value [unfalse] must be of type boolean for path [send_payer_email_to_gateway]"),
                arguments("replace", "provider_switch_enabled", null, "Field [value] is required"),
                arguments("replace", "provider_switch_enabled", "unfalse", "Value [unfalse] must be of type boolean for path [provider_switch_enabled]"),
                arguments("replace", "send_reference_to_gateway", null, "Field [value] is required"),
                arguments("replace", "send_reference_to_gateway", "unfalse", "Value [unfalse] must be of type boolean for path [send_reference_to_gateway]"),
                arguments("replace", "allow_authorisation_api", "unfalse", "Value [unfalse] must be of type boolean for path [allow_authorisation_api]"),
                arguments("replace", "recurring_enabled", "unfalse", "Value [unfalse] must be of type boolean for path [recurring_enabled]"),
                arguments("replace", "disabled", "unfalse", "Value [unfalse] must be of type boolean for path [disabled]"),
                arguments("replace", "disabled_reason", 42, "Value [42] must be a string for path [disabled_reason]")
        );
    }
}
