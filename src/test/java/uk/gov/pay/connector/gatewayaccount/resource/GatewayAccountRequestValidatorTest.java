package uk.gov.pay.connector.gatewayaccount.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import junitparams.converters.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.common.exception.ValidationException;
import uk.gov.pay.connector.common.validator.RequestValidator;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.fail;
import static uk.gov.pay.connector.common.model.api.jsonpatch.JsonPatchKeys.FIELD_OPERATION;
import static uk.gov.pay.connector.common.model.api.jsonpatch.JsonPatchKeys.FIELD_OPERATION_PATH;
import static uk.gov.pay.connector.common.model.api.jsonpatch.JsonPatchKeys.FIELD_VALUE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.FIELD_NOTIFY_API_TOKEN;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.FIELD_NOTIFY_PAYMENT_CONFIRMED_TEMPLATE_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.FIELD_NOTIFY_REFUND_ISSUED_TEMPLATE_ID;

@RunWith(JUnitParamsRunner.class)
public class GatewayAccountRequestValidatorTest {

    private GatewayAccountRequestValidator validator;
    private ObjectMapper objectMapper = new ObjectMapper();

    @Before
    public void before() {
        validator = new GatewayAccountRequestValidator(new RequestValidator());
    }

    @Test
    @Parameters({
            "bad, allow_apple_pay, true, Operation [bad] is not valid for path [allow_apple_pay]",
            "bad, allow_google_pay, true, Operation [bad] is not valid for path [allow_google_pay]",
            "replace, allow_apple_pay, null, Field [value] is required",
            "replace, allow_google_pay, null, Field [value] is required",
            "replace, allow_apple_pay, unfalse, Value [unfalse] must be of type boolean for path [allow_apple_pay]",
            "replace, allow_google_pay, unfalse, Value [unfalse] must be of type boolean for path [allow_google_pay]",
            "bad, allow_zero_amount, true, Operation [bad] is not valid for path [allow_zero_amount]",
            "replace, allow_zero_amount, null, Field [value] is required",
            "replace, allow_zero_amount, unfalse, Value [unfalse] must be of type boolean for path [allow_zero_amount]",
            "add, integration_version_3ds, 1, Operation [add] is not valid for path [integration_version_3ds]",
            "replace, integration_version_3ds, a-string, Value [a-string] is not valid for path [integration_version_3ds]",
            "replace, integration_version_3ds, 0, Value [0] is not valid for path [integration_version_3ds]",
            "replace, integration_version_3ds, 3, Value [3] is not valid for path [integration_version_3ds]",
            "add, block_prepaid_cards, true, Operation [add] is not valid for path [block_prepaid_cards]",
            "remove, block_prepaid_cards, true, Operation [remove] is not valid for path [block_prepaid_cards]",
            "bad, block_prepaid_cards, true, Operation [bad] is not valid for path [block_prepaid_cards]",
            "replace, block_prepaid_cards, null, Field [value] is required",
            "replace, block_prepaid_cards, unfalse, Value [unfalse] must be of type boolean for path [block_prepaid_cards]",
            "bad, allow_moto, true, Operation [bad] is not valid for path [allow_moto]",
            "replace, allow_moto, null, Field [value] is required",
            "replace, allow_moto, unfalse, Value [unfalse] must be of type boolean for path [allow_moto]",
            "bad, moto_mask_card_number_input, true, Operation [bad] is not valid for path [moto_mask_card_number_input]",
            "replace, moto_mask_card_number_input, null, Field [value] is required",
            "replace, moto_mask_card_number_input, unfalse, Value [unfalse] must be of type boolean for path [moto_mask_card_number_input]",
            "bad, moto_mask_card_security_code_input, true, Operation [bad] is not valid for path [moto_mask_card_security_code_input]",
            "replace, moto_mask_card_security_code_input, null, Field [value] is required",
            "replace, moto_mask_card_security_code_input, unfalse, Value [unfalse] must be of type boolean for path [moto_mask_card_security_code_input]",
            "replace, allow_telephone_payment_notifications, null, Field [value] is required",
            "replace, allow_telephone_payment_notifications, unfalse, Value [unfalse] must be of type boolean for path [allow_telephone_payment_notifications]",
            "replace, worldpay_exemption_engine_enabled, null, Field [value] is required",
            "replace, worldpay_exemption_engine_enabled, unfalse, Value [unfalse] must be of type boolean for path [worldpay_exemption_engine_enabled]",
            "replace, send_payer_ip_address_to_gateway, null, Field [value] is required",
            "replace, send_payer_ip_address_to_gateway, unfalse, Value [unfalse] must be of type boolean for path [send_payer_ip_address_to_gateway]",
            "replace, send_payer_email_to_gateway, null, Field [value] is required",
            "replace, send_payer_email_to_gateway, unfalse, Value [unfalse] must be of type boolean for path [send_payer_email_to_gateway]",
            "replace, provider_switch_enabled, null, Field [value] is required",
            "replace, provider_switch_enabled, unfalse, Value [unfalse] must be of type boolean for path [provider_switch_enabled]"
    })
    public void shouldThrowWhenRequestsAreInvalid(String op, String path, @Nullable String value, String expectedErrorMessage) {
        Map<String, String> patch = new HashMap<>() {{
            put(FIELD_OPERATION, op);
            put(FIELD_OPERATION_PATH, path);
        }};

        if (value != null) patch.put(FIELD_VALUE, value);

        JsonNode jsonNode = objectMapper.valueToTree(patch);
        try {
            validator.validatePatchRequest(jsonNode);
            fail("Expected ValidationException");
        } catch (ValidationException validationException) {
            assertThat(validationException.getErrors().size(), is(1));
            assertThat(validationException.getErrors(), hasItems(expectedErrorMessage));
        }
    }

    @Test
    public void shouldThrow_whenFieldsAreMissing() {
        JsonNode jsonNode = objectMapper.valueToTree(
                Map.of(FIELD_OPERATION, "replace", FIELD_OPERATION_PATH, "notify_settings"));
        try {
            validator.validatePatchRequest(jsonNode);
            fail("Expected ValidationException");
        } catch (ValidationException validationException) {
            assertThat(validationException.getErrors().size(), is(1));
            assertThat(validationException.getErrors(), hasItems(
                    "Field [value] is required"));
        }
    }

    @Test
    public void shouldThrow_whenFieldsNotValidInValue() {
        JsonNode jsonNode = objectMapper.valueToTree(
                Map.of(FIELD_OPERATION, "replace",
                        FIELD_OPERATION_PATH, "notify_settings",
                        FIELD_VALUE, Map.of("timbuktu", "anapitoken",
                                "colombo", "atemplateid")));
        try {
            validator.validatePatchRequest(jsonNode);
            fail("Expected ValidationException");
        } catch (ValidationException validationException) {
            assertThat(validationException.getErrors().size(), is(3));
            assertThat(validationException.getErrors(), hasItems(
                    "Field [api_token] is required",
                    "Field [template_id] is required",
                    "Field [refund_issued_template_id] is required"));
        }
    }

    @Test
    public void shouldThrow_whenInvalidPathOnOperation() {
        JsonNode jsonNode = objectMapper.valueToTree(Map.of(FIELD_OPERATION, "replace",
                FIELD_OPERATION_PATH, "service_name"));
        try {
            validator.validatePatchRequest(jsonNode);
            fail("Expected ValidationException");
        } catch (ValidationException validationException) {
            assertThat(validationException.getErrors().size(), is(1));
            assertThat(validationException.getErrors(), hasItems("Operation [op] not supported for path [service_name]"));
        }
    }

    @Test
    public void shouldThrow_whenEmailCollectionModeIsInvalid() {
        JsonNode jsonNode = objectMapper.valueToTree(
                Map.of(FIELD_OPERATION, "replace",
                        FIELD_OPERATION_PATH, "email_collection_mode",
                        FIELD_VALUE, "someValue"));
        try {
            validator.validatePatchRequest(jsonNode);
            fail("Expected ValidationException");
        } catch (ValidationException validationException) {
            assertThat(validationException.getErrors().size(), is(1));
            assertThat(validationException.getErrors(), hasItems("Value [someValue] is not valid for [email_collection_mode]"));
        }
    }

    @Test
    public void shouldNotThrow_whenAllValidationsPassed() {
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
    public void shouldThrow_whenInvalidOperation() {
        JsonNode jsonNode = objectMapper.valueToTree(
                Map.of(FIELD_OPERATION, "delete",
                        FIELD_OPERATION_PATH, "notify_settings",
                        FIELD_VALUE, Map.of(FIELD_NOTIFY_API_TOKEN, "anapitoken", 
                                FIELD_NOTIFY_PAYMENT_CONFIRMED_TEMPLATE_ID, "atemplateid")));
        try {
            validator.validatePatchRequest(jsonNode);
            fail("Expected ValidationException");
        } catch (ValidationException validationException) {
            assertThat(validationException.getErrors().size(), is(1));
            assertThat(validationException.getErrors(), hasItems("Operation [delete] is not valid for path [notify_settings]"));
        }
    }

    @Test
    public void shouldIgnoreEmptyOrMissingValue_whenRemoveOperation() {
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
        try {
            validator.validatePatchRequest(jsonNode);
            fail("Expected ValidationException");
        } catch (ValidationException validationException) {
            assertThat(validationException.getErrors().size(), is(1));
            assertThat(validationException.getErrors(), hasItems("Value [-100] is not valid for path [corporate_credit_card_surcharge_amount]"));
        }
    }

    @Test
    public void shouldThrow_whenIncorrectOperationForCorporateDebitSurchargeAmount() {
        JsonNode jsonNode = objectMapper.valueToTree(
                Map.of(FIELD_OPERATION, "remove",
                        FIELD_OPERATION_PATH, "corporate_debit_card_surcharge_amount",
                        FIELD_VALUE, 250));
        try {
            validator.validatePatchRequest(jsonNode);
            fail("Expected ValidationException");
        } catch (ValidationException validationException) {
            assertThat(validationException.getErrors().size(), is(1));
            assertThat(validationException.getErrors(), hasItems(
                    "Operation [remove] is not valid for path [corporate_debit_card_surcharge_amount]"));
        }
    }

    @Test
    public void shouldThrow_whenInvalidValueForCorporatePrepaidCreditSurchargeAmount() {
        JsonNode jsonNode = objectMapper.valueToTree(
                Map.of(FIELD_OPERATION, "replace",
                        FIELD_OPERATION_PATH, "corporate_prepaid_credit_card_surcharge_amount",
                        FIELD_VALUE, "not zero or a positive number that can be represented as a long"));
        try {
            validator.validatePatchRequest(jsonNode);
            fail("Expected ValidationException");
        } catch (ValidationException validationException) {
            assertThat(validationException.getErrors().size(), is(1));
            assertThat(validationException.getErrors(), hasItems(
                    "Value [not zero or a positive number that can be represented as a long] is not valid for path [corporate_prepaid_credit_card_surcharge_amount]"));
        }
    }

    @Test
    public void shouldThrow_whenMissingValueForCorporatePrepaidDebitSurchargeAmount() {
        JsonNode jsonNode = objectMapper.valueToTree(
                Map.of(FIELD_OPERATION, "replace", FIELD_OPERATION_PATH, "corporate_prepaid_debit_card_surcharge_amount"));
        try {
            validator.validatePatchRequest(jsonNode);
            fail("Expected ValidationException");
        } catch (ValidationException validationException) {
            assertThat(validationException.getErrors().size(), is(1));
            assertThat(validationException.getErrors(), hasItems("Field [value] is required"));
        }
    }

    @Test
    public void shouldThrow_whenNullValueForCorporatePrepaidDebitSurchargeAmount() {
        Map<String, Object> valueMap = new HashMap<>();
        valueMap.put("op", "replace");
        valueMap.put("path", "corporate_prepaid_debit_card_surcharge_amount");
        valueMap.put("value", null);
        JsonNode jsonNode = objectMapper.valueToTree(valueMap);
        try {
            validator.validatePatchRequest(jsonNode);
            fail("Expected ValidationException");
        } catch (ValidationException validationException) {
            assertThat(validationException.getErrors().size(), is(1));
            assertThat(validationException.getErrors(), hasItems("Field [value] is required"));
        }
    }

    @Test
    public void shouldThrow_whenEmptyValueForCorporatePrepaidDebitSurchargeAmount() {
        JsonNode jsonNode = objectMapper.valueToTree(
                Map.of(FIELD_OPERATION, "replace",
                        FIELD_OPERATION_PATH, "corporate_prepaid_debit_card_surcharge_amount",
                        FIELD_VALUE, ""));
        try {
            validator.validatePatchRequest(jsonNode);
            fail("Expected ValidationException");
        } catch (ValidationException validationException) {
            assertThat(validationException.getErrors().size(), is(1));
            assertThat(validationException.getErrors(), hasItems("Value [] is not valid for path [corporate_prepaid_debit_card_surcharge_amount]"));
        }
    }
}
