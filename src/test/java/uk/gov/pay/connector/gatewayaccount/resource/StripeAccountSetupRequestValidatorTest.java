package uk.gov.pay.connector.gatewayaccount.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import uk.gov.pay.connector.common.exception.ValidationException;
import uk.gov.pay.connector.common.validator.RequestValidator;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasProperty;

public class StripeAccountSetupRequestValidatorTest {
    
    private final StripeAccountSetupRequestValidator validator = new StripeAccountSetupRequestValidator(new RequestValidator());

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void replaceBankAccountTrueIsValid() {
        JsonNode jsonNode = new ObjectMapper().valueToTree(Collections.singletonList(ImmutableMap.of(
                "op", "replace",
                "path", "bank_account",
                "value", true)));
        
        validator.validatePatchRequest(jsonNode);
    }

    @Test
    public void replaceBankAccountFalseIsValid() {
        JsonNode jsonNode = new ObjectMapper().valueToTree(Collections.singletonList(ImmutableMap.of(
                "op", "replace",
                "path", "bank_account",
                "value", false)));

        validator.validatePatchRequest(jsonNode);
    }

    @Test
    public void replaceResponsiblePersonTrueIsValid() {
        JsonNode jsonNode = new ObjectMapper().valueToTree(Collections.singletonList(ImmutableMap.of(
                "op", "replace",
                "path", "responsible_person",
                "value", true)));

        validator.validatePatchRequest(jsonNode);
    }

    @Test
    public void multipleUpdatesAreValid() {
        JsonNode jsonNode = new ObjectMapper().valueToTree(Arrays.asList(
                ImmutableMap.of(
                        "op", "replace",
                        "path", "bank_account",
                        "value", true),
                ImmutableMap.of(
                        "op", "replace",
                        "path", "responsible_person",
                        "value", false),
                ImmutableMap.of(
                        "op", "replace",
                        "path", "organisation_details",
                        "value", true)
                ));

        validator.validatePatchRequest(jsonNode);

    }

    @Test
    public void replaceResponsiblePersonFalseIsValid() {
        JsonNode jsonNode = new ObjectMapper().valueToTree(Collections.singletonList(ImmutableMap.of(
                "op", "replace",
                "path", "responsible_person",
                "value", false)));

        validator.validatePatchRequest(jsonNode);
    }

    @Test
    public void replaceOrganisationDetailsTrueIsValid() {
        JsonNode jsonNode = new ObjectMapper().valueToTree(Collections.singletonList(ImmutableMap.of(
                "op", "replace",
                "path", "organisation_details",
                "value", true)));

        validator.validatePatchRequest(jsonNode);
    }

    @Test
    public void replaceOrganisationDetailsFalseIsValid() {
        JsonNode jsonNode = new ObjectMapper().valueToTree(Collections.singletonList(ImmutableMap.of(
                "op", "replace",
                "path", "organisation_details",
                "value", false)));

        validator.validatePatchRequest(jsonNode);
    }

    @Test
    public void opNotReplaceIsInvalid() {
        JsonNode jsonNode = new ObjectMapper().valueToTree(Collections.singletonList(ImmutableMap.of(
                "op", "add",
                "path", "bank_account",
                "value", true)));

        expectedException.expect(ValidationException.class);
        expectedException.expect(hasProperty("errors", contains("Operation [add] not supported for path [bank_account]")));
        
        validator.validatePatchRequest(jsonNode);
    }

    @Test
    public void opIsNotStringIsInvalid() {
        JsonNode jsonNode = new ObjectMapper().valueToTree(Collections.singletonList(ImmutableMap.of(
                "op", 123,
                "path", "bank_account",
                "value", true)));

        expectedException.expect(ValidationException.class);
        expectedException.expect(hasProperty("errors", contains("Field [op] must be a string")));

        validator.validatePatchRequest(jsonNode);
    }

    @Test
    public void opIsEmptyStringIsInvalid() {
        JsonNode jsonNode = new ObjectMapper().valueToTree(Collections.singletonList(ImmutableMap.of(
                "op", "",
                "path", "bank_account",
                "value", true)));

        expectedException.expect(ValidationException.class);
        expectedException.expect(hasProperty("errors", contains("Field [op] is required")));

        validator.validatePatchRequest(jsonNode);
    }

    @Test
    public void opIsNullIsInvalid() {
        JsonNode jsonNode = new ObjectMapper().valueToTree(Collections.singletonList(new HashMap<String, Object>() {{
            put("op", null);
            put("path", "bank_account");
            put("value", true);
        }}));

        expectedException.expect(ValidationException.class);
        expectedException.expect(hasProperty("errors", contains("Field [op] is required")));

        validator.validatePatchRequest(jsonNode);
    }

    @Test
    public void opMissingIsInvalid() {
        JsonNode jsonNode = new ObjectMapper().valueToTree(Collections.singletonList(ImmutableMap.of(
                "path", "bank_account",
                "value", true)));

        expectedException.expect(ValidationException.class);
        expectedException.expect(hasProperty("errors", contains("Field [op] is required")));

        validator.validatePatchRequest(jsonNode);
    }

    @Test
    public void pathNotAllowedIsInvalid() {
        JsonNode jsonNode = new ObjectMapper().valueToTree(Collections.singletonList(ImmutableMap.of(
                "op", "add",
                "path", "blood_sample_deposited",
                "value", true)));

        expectedException.expect(ValidationException.class);
        expectedException.expect(hasProperty("errors", contains("Field [path] must be one of [bank_account, organisation_details, responsible_person]")));

        validator.validatePatchRequest(jsonNode);
    }

    @Test
    public void pathIsNotStringIsInvalid() {
        JsonNode jsonNode = new ObjectMapper().valueToTree(Collections.singletonList(ImmutableMap.of(
                "op", "replace",
                "path", 1234,
                "value", true)));

        expectedException.expect(ValidationException.class);
        expectedException.expect(hasProperty("errors", contains("Field [path] must be a string")));

        validator.validatePatchRequest(jsonNode);
    }

    @Test
    public void pathIsEmptyStringIsInvalid() {
        JsonNode jsonNode = new ObjectMapper().valueToTree(Collections.singletonList(ImmutableMap.of(
                "op", "replace",
                "path", "",
                "value", true)));

        expectedException.expect(ValidationException.class);
        expectedException.expect(hasProperty("errors", contains("Field [path] is required")));

        validator.validatePatchRequest(jsonNode);
    }

    @Test
    public void pathIsNullIsInvalid() {
        JsonNode jsonNode = new ObjectMapper().valueToTree(Collections.singletonList(new HashMap<String, Object>() {{
            put("op", "replace");
            put("path", null);
            put("value", true);
        }}));

        expectedException.expect(ValidationException.class);
        expectedException.expect(hasProperty("errors", contains("Field [path] is required")));

        validator.validatePatchRequest(jsonNode);
    }

    @Test
    public void pathMissingIsInvalid() {
        JsonNode jsonNode = new ObjectMapper().valueToTree(Collections.singletonList(ImmutableMap.of(
                "op", "replace",
                "value", true)));

        expectedException.expect(ValidationException.class);
        expectedException.expect(hasProperty("errors", contains("Field [path] is required")));

        validator.validatePatchRequest(jsonNode);
    }
    
    @Test
    public void valueIsNotBooleanIsInvalid() {
        JsonNode jsonNode = new ObjectMapper().valueToTree(Collections.singletonList(ImmutableMap.of(
                "op", "replace",
                "path", "bank_account",
                "value", "true")));

        expectedException.expect(ValidationException.class);
        expectedException.expect(hasProperty("errors", contains("Field [value] must be a boolean")));

        validator.validatePatchRequest(jsonNode);
    }
    
    @Test
    public void valueIsNullIsInvalid() {
        JsonNode jsonNode = new ObjectMapper().valueToTree(Collections.singletonList(new HashMap<String, Object>() {{
            put("op", "replace");
            put("path", "bank_account");
            put("value", null);
        }}));

        expectedException.expect(ValidationException.class);
        expectedException.expect(hasProperty("errors", contains("Field [value] is required")));

        validator.validatePatchRequest(jsonNode);
    }

    @Test
    public void valueMissingIsInvalid() {
        JsonNode jsonNode = new ObjectMapper().valueToTree(Collections.singletonList(ImmutableMap.of(
                "op", "replace",
                "path", "bank_account")));

        expectedException.expect(ValidationException.class);
        expectedException.expect(hasProperty("errors", contains("Field [value] is required")));

        validator.validatePatchRequest(jsonNode);
    }

    @Test
    public void notAnArrayIsInvalid() {
        JsonNode jsonNode = new ObjectMapper().valueToTree(ImmutableMap.of(
                "op", "replace",
                "path", "bank_account",
                "value", true));

        expectedException.expect(ValidationException.class);
        expectedException.expect(hasProperty("errors", contains("JSON is not an array")));

        validator.validatePatchRequest(jsonNode);
    }
}
