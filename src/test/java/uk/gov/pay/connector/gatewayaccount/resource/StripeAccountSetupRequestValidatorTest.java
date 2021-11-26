package uk.gov.pay.connector.gatewayaccount.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import junitparams.converters.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.service.payments.commons.api.exception.ValidationException;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasProperty;
import static org.junit.Assert.assertThrows;

@RunWith(JUnitParamsRunner.class)
public class StripeAccountSetupRequestValidatorTest {

    private final StripeAccountSetupRequestValidator validator = new StripeAccountSetupRequestValidator();
    private static ObjectMapper objectMapper = new ObjectMapper();

    @Parameters({
            "replace, bank_account, true",
            "replace, bank_account, false",
            "replace, responsible_person, true",
            "replace, responsible_person, false",
            "replace, vat_number, true",
            "replace, vat_number, false",
            "replace, company_number, true",
            "replace, company_number, false",
            "replace, director, true",
            "replace, director, false",
            "replace, government_entity_document, true",
            "replace, government_entity_document, false",
    })
    @Test
    public void shouldAllowReplaceOperationForValidPathsAndValues(String operation, String path, boolean value) {
        JsonNode jsonNode = objectMapper.valueToTree(Collections.singletonList(Map.of(
                "op", operation,
                "path", path,
                "value", value)));

        validator.validatePatchRequest(jsonNode);
    }

    private Object[] invalidData() {
        return new Object[]{
                Map.of("operation", "add", "path", "bank_account", "value", true,
                        "expectedErrorMessage", "Operation [add] not supported for path [bank_account]"),
                Map.of("operation", "add", "path", "blood_sample_deposited", "value", true,
                        "expectedErrorMessage", "Field [path] must be one of [additional_kyc_data, bank_account, company_number, director, government_entity_document, responsible_person, vat_number]"),

                Map.of("expectedErrorMessage", "Field [path] is required", "operation", "replace", "value", true),
                Map.of("expectedErrorMessage", "Field [path] is required", "operation", "replace", "path", "", "value", true),
                Map.of("expectedErrorMessage", "Field [path] must be a string", "operation", "replace", "path", 1234, "value", true),
                new HashMap<String, Object>() {{put("expectedErrorMessage", "Field [path] is required"); put("operation", "replace"); put("path", null); put("value", true);}},

                Map.of("expectedErrorMessage", "Field [op] is required", "operation", "", "path", "bank_account", "value", true),
                Map.of("expectedErrorMessage", "Field [op] is required", "path", "bank_account", "value", true),
                Map.of("expectedErrorMessage", "Field [op] must be a string", "operation", 123, "path", "bank_account", "value", true),
                new HashMap<String, Object>() {{put("expectedErrorMessage", "Field [op] is required"); put("operation", null); put("path", "bank_account"); put("value", true);}},

                Map.of("expectedErrorMessage", "Field [value] is required", "operation", "replace", "path", "bank_account"),
                Map.of("expectedErrorMessage", "Value for path [bank_account] must be a boolean", "operation", "replace", "path", "bank_account", "value", ""),
                Map.of("expectedErrorMessage", "Value for path [bank_account] must be a boolean", "operation", "replace", "path", "bank_account", "value", "true"),
                new HashMap<String, Object>() {{put("expectedErrorMessage", "Field [value] is required"); put("operation", "replace"); put("path", "bank_account"); put("value", null);}},

                Map.of("expectedErrorMessage", "Field [op] is required", "operation", "", "path", "director", "value", true),
                Map.of("expectedErrorMessage", "Field [op] is required", "path", "director", "value", true),
                Map.of("expectedErrorMessage", "Field [op] must be a string", "operation", 123, "path", "director", "value", true),
                new HashMap<String, Object>() {{put("expectedErrorMessage", "Field [op] is required"); put("operation", null); put("path", "director"); put("value", true);}},               

                Map.of("expectedErrorMessage", "Field [op] is required", "operation", "", "path", "government_entity_document", "value", true),
                Map.of("expectedErrorMessage", "Field [op] is required", "path", "government_entity_document", "value", true),
                Map.of("expectedErrorMessage", "Field [op] must be a string", "operation", 123, "path", "government_entity_document", "value", true),
                new HashMap<String, Object>() {{put("expectedErrorMessage", "Field [op] is required"); put("operation", null); put("path", "government_entity_document"); put("value", true);}},
        };
    }
    @Parameters(method = "invalidData")
    @Test
    public void shouldThrowExceptionForInvalidValues(@Nullable  Map<Object, Object> data) {
        JsonNode jsonNode = getJsonNode(data);

        ValidationException validationException = assertThrows(ValidationException.class,
                () -> validator.validatePatchRequest(jsonNode));

        assertThat(validationException, hasProperty("errors", contains(data.get("expectedErrorMessage").toString())));
    }

    private JsonNode getJsonNode(Map<Object, Object> data) {
        Map<Object, Object> params = new HashMap<>();
        if (data.containsKey("operation")) {
            params.put("op", data.get("operation"));
        }
        if (data.containsKey("path")) {
            params.put("path", data.get("path"));
        }
        if (data.containsKey("value")) {
            params.put("value", data.get("value"));
        }

        return objectMapper.valueToTree(Collections.singletonList(params));
    }

    @Test
    public void multipleUpdatesAreValid() {
        JsonNode jsonNode = objectMapper.valueToTree(Arrays.asList(
                Map.of(
                        "op", "replace",
                        "path", "bank_account",
                        "value", true),
                Map.of(
                        "op", "replace",
                        "path", "responsible_person",
                        "value", false),
                Map.of(
                        "op", "replace",
                        "path", "vat_number",
                        "value", true)
        ));

        validator.validatePatchRequest(jsonNode);
    }

    @Test
    public void notAnArrayIsInvalid() {
        JsonNode jsonNode = objectMapper.valueToTree(Map.of(
                "op", "replace",
                "path", "bank_account",
                "value", true));

        ValidationException validationException = assertThrows(ValidationException.class,
                () -> validator.validatePatchRequest(jsonNode));

        assertThat(validationException, hasProperty("errors", contains("JSON is not an array")));
    }
}
