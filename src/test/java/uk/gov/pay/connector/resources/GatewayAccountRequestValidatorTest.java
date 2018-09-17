package uk.gov.pay.connector.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.exception.ValidationException;
import uk.gov.pay.connector.validations.RequestValidator;

import static junit.framework.TestCase.fail;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.model.domain.GatewayAccount.FIELD_NOTIFY_API_TOKEN;
import static uk.gov.pay.connector.model.domain.GatewayAccount.FIELD_NOTIFY_TEMPLATE_ID;
import static uk.gov.pay.connector.model.domain.GatewayAccount.FIELD_OPERATION;
import static uk.gov.pay.connector.model.domain.GatewayAccount.FIELD_OPERATION_PATH;
import static uk.gov.pay.connector.model.domain.GatewayAccount.FIELD_VALUE;

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
            assertThat(validationException.getErrors().size(), is(2));
            assertThat(validationException.getErrors(), hasItems(
                    "Field [api_token] is required",
                    "Field [template_id] is required"));
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
    public void shouldNotThrow_whenAllValidationsPassed() {
        JsonNode jsonNode = new ObjectMapper()
                .valueToTree(ImmutableMap.of(FIELD_OPERATION, "replace",
                        FIELD_OPERATION_PATH, "notify_settings",
                        FIELD_VALUE, ImmutableMap.of(FIELD_NOTIFY_API_TOKEN, "anapitoken",
                                FIELD_NOTIFY_TEMPLATE_ID, "atemplateid")));
        
        validator.validatePatchRequest(jsonNode);
    }

    @Test
    public void shouldNotThrow_whenPathIsEmailCollectionMode() {
        JsonNode jsonNode = new ObjectMapper()
                .valueToTree(ImmutableMap.of(
                        FIELD_OPERATION, "replace",
                        FIELD_OPERATION_PATH, "email_collection_mode",
                        FIELD_VALUE,"someValue"));
        validator.validatePatchRequest(jsonNode);
    }

    @Test
    public void shouldThrow_whenInvalidOperation() {
        JsonNode jsonNode = new ObjectMapper()
                .valueToTree(ImmutableMap.of(FIELD_OPERATION, "delete",
                        FIELD_OPERATION_PATH, "notify_settings",
                        FIELD_VALUE, ImmutableMap.of(FIELD_NOTIFY_API_TOKEN, "anapitoken",
                                FIELD_NOTIFY_TEMPLATE_ID, "atemplateid")));
        try {
            validator.validatePatchRequest(jsonNode);
            fail( "Expected ValidationException" );
        } catch (ValidationException validationException) {
            assertThat(validationException.getErrors().size(), is(1));
            assertThat(validationException.getErrors(), hasItems("Operation [delete] is not valid for path [op]"));
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
}
