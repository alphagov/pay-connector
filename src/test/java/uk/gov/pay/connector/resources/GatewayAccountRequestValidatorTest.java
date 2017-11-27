package uk.gov.pay.connector.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.util.Errors;
import uk.gov.pay.connector.validations.RequestValidator;

import java.util.Optional;

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
    public void before() throws Exception {
        validator = new GatewayAccountRequestValidator(new RequestValidator());
        objectMapper = new ObjectMapper();
    }

    @Test
    public void shouldError_whenFieldsAreMissing() throws Exception {
        JsonNode jsonNode = new ObjectMapper()
                .valueToTree(ImmutableMap.of(FIELD_OPERATION, "replace",
                        FIELD_OPERATION_PATH, "notify_settings"));
        Optional<Errors> optionalErrors = validator.validatePatchRequest(jsonNode);

        assertThat(optionalErrors.isPresent(), is(true));
        Errors errors = optionalErrors.get();

        assertThat(errors.getErrors().size(), is(1));
        assertThat(errors.getErrors(), hasItems(
                "Field [value] is required"));
    }

    @Test
    public void shouldError_whenFieldsNotValidInValue() throws Exception {
        JsonNode jsonNode = new ObjectMapper()
                .valueToTree(ImmutableMap.of(FIELD_OPERATION, "replace",
                        FIELD_OPERATION_PATH, "notify_settings",
                        FIELD_VALUE, ImmutableMap.of("timbuktu", "anapitoken",
                                "colombo", "atemplateid")));
        Optional<Errors> optionalErrors = validator.validatePatchRequest(jsonNode);

        assertThat(optionalErrors.isPresent(), is(true));
        assertThat(optionalErrors.get().getErrors().size(), is(2));
        assertThat(optionalErrors.get().getErrors().get(0), is("Field [api_token] is required"));
        assertThat(optionalErrors.get().getErrors().get(1), is("Field [template_id] is required"));
    }

    @Test
    public void shouldReturnError_whenInvalidPathOnOperation() throws Exception {
        JsonNode jsonNode = objectMapper.valueToTree(ImmutableMap.of(FIELD_OPERATION, "replace",
                FIELD_OPERATION_PATH, "service_name"));
        Optional<Errors> optionalErrors =validator.validatePatchRequest(jsonNode);
        assertThat(optionalErrors.isPresent(), is(true));
        assertThat(optionalErrors.get().getErrors().size(), is(1));
        assertThat(optionalErrors.get().getErrors().get(0), is("Operation [op] not supported for path [service_name]"));
    }

    @Test
    public void shouldReturnEmpty_whenAllValidationsPassed() throws Exception {
        JsonNode jsonNode = new ObjectMapper()
                .valueToTree(ImmutableMap.of(FIELD_OPERATION, "replace",
                FIELD_OPERATION_PATH, "notify_settings",
                        FIELD_VALUE, ImmutableMap.of(FIELD_NOTIFY_API_TOKEN, "anapitoken",
                                FIELD_NOTIFY_TEMPLATE_ID, "atemplateid")));
        Optional<Errors> optionalErrors = validator.validatePatchRequest(jsonNode);

        assertThat(optionalErrors.isPresent(), is(false));
    }

    @Test
    public void shouldReturnError_whenInvalidOperation() throws Exception {
        JsonNode jsonNode = new ObjectMapper()
                .valueToTree(ImmutableMap.of(FIELD_OPERATION, "delete",
                        FIELD_OPERATION_PATH, "notify_settings",
                        FIELD_VALUE, ImmutableMap.of(FIELD_NOTIFY_API_TOKEN, "anapitoken",
                                FIELD_NOTIFY_TEMPLATE_ID, "atemplateid")));
        Optional<Errors> optionalErrors = validator.validatePatchRequest(jsonNode);

        assertThat(optionalErrors.isPresent(), is(true));
        assertThat(optionalErrors.get().getErrors().size(), is(1));
        assertThat(optionalErrors.get().getErrors().get(0), is("Operation [delete] is not valid for path [op]"));
    }

    @Test
    public void shouldIgnoreEmptyOrMissingValue_whenRemoveOperation() throws Exception {
        JsonNode jsonNode = new ObjectMapper()
                .valueToTree(ImmutableMap.of(FIELD_OPERATION, "remove",
                        FIELD_OPERATION_PATH, "notify_settings",
                        FIELD_VALUE, ImmutableMap.of(FIELD_NOTIFY_API_TOKEN, "")));
        Optional<Errors> optionalErrors = validator.validatePatchRequest(jsonNode);

        assertThat(optionalErrors.isPresent(), is(false));
    }
}
