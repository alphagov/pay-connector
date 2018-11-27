package uk.gov.pay.connector.gatewayaccount.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.common.exception.ValidationException;
import uk.gov.pay.connector.common.validator.RequestValidator;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.fail;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.FIELD_NOTIFY_API_TOKEN;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.FIELD_NOTIFY_PAYMENT_CONFIRMED_TEMPLATE_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.FIELD_NOTIFY_REFUND_ISSUED_TEMPLATE_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.FIELD_OPERATION;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.FIELD_OPERATION_PATH;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.FIELD_VALUE;

public class GatewayAccountRequestValidatorTest {

    private GatewayAccountRequestValidator validator;

    private ObjectMapper objectMapper;

    @Before
    public void before() {
        validator = new GatewayAccountRequestValidator(new RequestValidator());
        objectMapper = new ObjectMapper();
    }

    @Test
    public void shouldThrow_whenFieldsAreMissing() {
        JsonNode jsonNode = new ObjectMapper()
                .valueToTree(ImmutableMap.of(FIELD_OPERATION, "replace",
                        FIELD_OPERATION_PATH, "notify_settings"));
        try {
            validator.validatePatchRequest(jsonNode);
            fail( "Expected ValidationException" );
        } catch (ValidationException validationException) {
            assertThat(validationException.getErrors().size(), is(1));
            assertThat(validationException.getErrors(), hasItems(
                    "Field [value] is required"));
        }
    }

    @Test
    public void shouldThrow_whenFieldsNotValidInValue() {
        JsonNode jsonNode = new ObjectMapper()
                .valueToTree(ImmutableMap.of(FIELD_OPERATION, "replace",
                        FIELD_OPERATION_PATH, "notify_settings",
                        FIELD_VALUE, ImmutableMap.of("timbuktu", "anapitoken",
                                "colombo", "atemplateid")));
        try {
            validator.validatePatchRequest(jsonNode);
            fail( "Expected ValidationException" );
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
        JsonNode jsonNode = objectMapper.valueToTree(ImmutableMap.of(FIELD_OPERATION, "replace",
                FIELD_OPERATION_PATH, "service_name"));
        try {
            validator.validatePatchRequest(jsonNode);
            fail( "Expected ValidationException" );
        } catch (ValidationException validationException) {
            assertThat(validationException.getErrors().size(), is(1));
            assertThat(validationException.getErrors(), hasItems("Operation [op] not supported for path [service_name]"));
        }
    }

    @Test
    public void shouldThrow_whenEmailCollectionModeIsInvalid() {
        JsonNode jsonNode = new ObjectMapper()
                .valueToTree(ImmutableMap.of(
                        FIELD_OPERATION, "replace",
                        FIELD_OPERATION_PATH, "email_collection_mode",
                        FIELD_VALUE,"someValue"));
        try {
            validator.validatePatchRequest(jsonNode);
            fail( "Expected ValidationException" );
        } catch (ValidationException validationException) {
            assertThat(validationException.getErrors().size(), is(1));
            assertThat(validationException.getErrors(), hasItems("Value [someValue] is not valid for [email_collection_mode]"));
        }
    }

    @Test
    public void shouldNotThrow_whenAllValidationsPassed() {
        JsonNode jsonNode = new ObjectMapper()
                .valueToTree(ImmutableMap.of(FIELD_OPERATION, "replace",
                        FIELD_OPERATION_PATH, "notify_settings",
                        FIELD_VALUE, ImmutableMap.of(
                                FIELD_NOTIFY_API_TOKEN, "anapitoken",
                                FIELD_NOTIFY_PAYMENT_CONFIRMED_TEMPLATE_ID, "atemplateid",
                                FIELD_NOTIFY_REFUND_ISSUED_TEMPLATE_ID, "anothertemplateid")));
        
        validator.validatePatchRequest(jsonNode);
    }

    @Test
    public void shouldNotThrow_whenPathIsValidEmailCollectionMode() {
        JsonNode jsonNode = new ObjectMapper()
                .valueToTree(ImmutableMap.of(
                        FIELD_OPERATION, "replace",
                        FIELD_OPERATION_PATH, "email_collection_mode",
                        FIELD_VALUE,"MANDATORY"));
        validator.validatePatchRequest(jsonNode);
    }

    @Test
    public void shouldThrow_whenInvalidOperation() {
        JsonNode jsonNode = new ObjectMapper()
                .valueToTree(ImmutableMap.of(FIELD_OPERATION, "delete",
                        FIELD_OPERATION_PATH, "notify_settings",
                        FIELD_VALUE, ImmutableMap.of(FIELD_NOTIFY_API_TOKEN, "anapitoken",
                                FIELD_NOTIFY_PAYMENT_CONFIRMED_TEMPLATE_ID, "atemplateid")));
        try {
            validator.validatePatchRequest(jsonNode);
            fail( "Expected ValidationException" );
        } catch (ValidationException validationException) {
            assertThat(validationException.getErrors().size(), is(1));
            assertThat(validationException.getErrors(), hasItems("Operation [delete] is not valid for path [notify_settings]"));
        }
    }

    @Test
    public void shouldIgnoreEmptyOrMissingValue_whenRemoveOperation() {
        JsonNode jsonNode = new ObjectMapper()
                .valueToTree(ImmutableMap.of(FIELD_OPERATION, "remove",
                        FIELD_OPERATION_PATH, "notify_settings",
                        FIELD_VALUE, ImmutableMap.of(FIELD_NOTIFY_API_TOKEN, "")));
        validator.validatePatchRequest(jsonNode);
    }
    
    @Test
    public void shouldThrow_whenAllowWebPaymentIsInvalid() {
        JsonNode jsonNode = new ObjectMapper()
                .valueToTree(ImmutableMap.of(FIELD_OPERATION, "replace",
                        FIELD_OPERATION_PATH, "allow_web_payments",
                        FIELD_VALUE, "unfalse"));
        try {
            validator.validatePatchRequest(jsonNode);
            fail( "Expected ValidationException" );
        } catch (ValidationException validationException) {
            assertThat(validationException.getErrors().size(), is(1));
            assertThat(validationException.getErrors(), hasItems("Value [unfalse] is not valid for [allow_web_payments]"));
        }
    }
    
    @Test
    public void shouldThrow_whenCorporateCreditSurchargeAmountIsInvalid() {
        JsonNode jsonNode = new ObjectMapper()
                .valueToTree(ImmutableMap.of(FIELD_OPERATION, "replace",
                        FIELD_OPERATION_PATH, "corporate_credit_card_surcharge_amount",
                        FIELD_VALUE, -100));
        try {
            validator.validatePatchRequest(jsonNode);
            fail( "Expected ValidationException" );
        } catch (ValidationException validationException) {
            assertThat(validationException.getErrors().size(), is(1));
            assertThat(validationException.getErrors(), hasItems("Value [-100] is not valid for path [corporate_credit_card_surcharge_amount]"));
        }
    }

    @Test
    public void shouldThrow_whenIncorrectOperationForCorporateDebitSurchargeAmount() {
        JsonNode jsonNode = new ObjectMapper()
                .valueToTree(ImmutableMap.of(FIELD_OPERATION, "remove",
                        FIELD_OPERATION_PATH, "corporate_debit_card_surcharge_amount",
                        FIELD_VALUE, 250));
        try {
            validator.validatePatchRequest(jsonNode);
            fail( "Expected ValidationException" );
        } catch (ValidationException validationException) {
            assertThat(validationException.getErrors().size(), is(1));
            assertThat(validationException.getErrors(), hasItems(
                    "Operation [remove] is not valid for path [corporate_debit_card_surcharge_amount]"));
        }
    }
    
    @Test
    public void shouldThrow_whenInvalidValueForCorporatePrepaidCreditSurchargeAmount() {
        JsonNode jsonNode = new ObjectMapper()
                .valueToTree(ImmutableMap.of(FIELD_OPERATION, "replace",
                        FIELD_OPERATION_PATH, "corporate_prepaid_credit_card_surcharge_amount",
                        FIELD_VALUE, "not zero or a positive number that can be represented as a long"));
        try {
            validator.validatePatchRequest(jsonNode);
            fail( "Expected ValidationException" );
        } catch (ValidationException validationException) {
            assertThat(validationException.getErrors().size(), is(1));
            assertThat(validationException.getErrors(), hasItems(
                    "Value [not zero or a positive number that can be represented as a long] is not valid for path [corporate_prepaid_credit_card_surcharge_amount]"));
        }
    }
    
    @Test
    public void shouldThrow_whenMissingValueForCorporatePrepaidDebitSurchargeAmount() {
        JsonNode jsonNode = new ObjectMapper()
                .valueToTree(ImmutableMap.of(FIELD_OPERATION, "replace",
                        FIELD_OPERATION_PATH, "corporate_prepaid_debit_card_surcharge_amount"));
        try {
            validator.validatePatchRequest(jsonNode);
            fail( "Expected ValidationException" );
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
        JsonNode jsonNode = new ObjectMapper().valueToTree(valueMap);
        try {
            validator.validatePatchRequest(jsonNode);
            fail( "Expected ValidationException" );
        } catch (ValidationException validationException) {
            assertThat(validationException.getErrors().size(), is(1));
            assertThat(validationException.getErrors(), hasItems("Field [value] is required"));
        }
    }

    @Test
    public void shouldThrow_whenEmptyValueForCorporatePrepaidDebitSurchargeAmount() {
        JsonNode jsonNode = new ObjectMapper()
                .valueToTree(ImmutableMap.of(FIELD_OPERATION, "replace",
                        FIELD_OPERATION_PATH, "corporate_prepaid_debit_card_surcharge_amount",
                        FIELD_VALUE, ""));
        try {
            validator.validatePatchRequest(jsonNode);
            fail( "Expected ValidationException" );
        } catch (ValidationException validationException) {
            assertThat(validationException.getErrors().size(), is(1));
            assertThat(validationException.getErrors(), hasItems("Value [] is not valid for path [corporate_prepaid_debit_card_surcharge_amount]"));
        }
    }
}
