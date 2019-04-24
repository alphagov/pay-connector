package uk.gov.pay.connector.charge.model;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import uk.gov.pay.commons.model.charge.ExternalMetadata;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;


public class ExternalMetadataTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final ObjectMapper mapper = new ObjectMapper();

    private final String TOO_LONG_VALUE = "This value is over fifty characters long and is invalid!";
    private final String TOO_LONG_KEY = "This key is over thirty characters long and is invalid!";

    @Test
    public void shouldPassValidation() {
        Map<String, Object> metadata = Map.of(
                "key1", "string",
                "key2", true,
                "key3", 123,
                "key4", 1.234,
                "key5", ""
        );
        ExternalMetadata validExternalMetadata = new ExternalMetadata(metadata);

        Set<ConstraintViolation<ExternalMetadata>> violations = validator.validate(validExternalMetadata);

        assertThat(violations.size(), is(0));
    }

    @Test
    public void shouldFailValidationWithMoreThan10Keys() {
        Map<String, Object> metadata = IntStream.rangeClosed(1, 11).boxed()
                .collect(Collectors.toUnmodifiableMap(i -> "key" + i, i-> "value" + i));
        ExternalMetadata invalidExternalMetadata = new ExternalMetadata(metadata);

        Set<ConstraintViolation<ExternalMetadata>> violations = validator.validate(invalidExternalMetadata);

        assertThat(violations.size(), is(1));
        assertThat(violations.iterator().next().getMessage(), is("Field [metadata] cannot have more than 10 key-value pairs"));
    }

    @Test
    public void shouldFailValidationForKeysToLong() {
        Map<String, Object> metadata = Map.of(TOO_LONG_KEY, "string");
        ExternalMetadata invalidExternalMetadata = new ExternalMetadata(metadata);

        Set<ConstraintViolation<ExternalMetadata>> violations = validator.validate(invalidExternalMetadata);

        assertThat(violations.size(), is(1));
        assertThat(violations.iterator().next().getMessage(), is("Field [metadata] keys must be between 1 and 30 characters long"));
    }

    @Test
    public void shouldFailValidationForEmptyKey() {
        Map<String, Object> metadata = Map.of("", "string");
        ExternalMetadata invalidExternalMetadata = new ExternalMetadata(metadata);

        Set<ConstraintViolation<ExternalMetadata>> violations = validator.validate(invalidExternalMetadata);

        assertThat(violations.size(), is(1));
        assertThat(violations.iterator().next().getMessage(), is("Field [metadata] keys must be between 1 and 30 characters long"));
    }

    @Test
    public void shouldFailValidationForValueToLong() {
        Map<String, Object> metadata = Map.of("key1", TOO_LONG_VALUE);
        ExternalMetadata invalidExternalMetadata = new ExternalMetadata(metadata);

        Set<ConstraintViolation<ExternalMetadata>> violations = validator.validate(invalidExternalMetadata);

        assertThat(violations.size(), is(1));
        assertThat(violations.iterator().next().getMessage(), is("Field [metadata] values must be no greater than 50 characters long"));
    }

    @Test
    public void shouldFailValidationWhenValueIsObject() {
        Map<String, Object> metadata = Map.of("key1", mapper.createObjectNode());
        ExternalMetadata invalidExternalMetadata = new ExternalMetadata(metadata);

        Set<ConstraintViolation<ExternalMetadata>> violations = validator.validate(invalidExternalMetadata);

        assertThat(violations.size(), is(1));
        assertThat(violations.iterator().next().getMessage(), is("Field [metadata] values must be of type String, Boolean or Number"));
    }

    @Test
    public void shouldFailValidationWhenValueIsArray() {
        Map<String, Object> metadata = Map.of("key1", mapper.createArrayNode());
        ExternalMetadata invalidExternalMetadata = new ExternalMetadata(metadata);

        Set<ConstraintViolation<ExternalMetadata>> violations = validator.validate(invalidExternalMetadata);

        assertThat(violations.size(), is(1));
        assertThat(violations.iterator().next().getMessage(), is("Field [metadata] values must be of type String, Boolean or Number"));
    }

    @Test
    public void shouldFailValidationWhenAValueIsNull() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("key1", null);
        ExternalMetadata invalidExternalMetadata = new ExternalMetadata(metadata);

        Set<ConstraintViolation<ExternalMetadata>> violations = validator.validate(invalidExternalMetadata);

        assertThat(violations.size(), is(1));
        assertThat(violations.iterator().next().getMessage(), is("Field [metadata] must not have null values"));
    }

    @Test
    public void shouldFailWithMultipleViolations() {
        Map<String, Object> metadata = Map.of(
                TOO_LONG_KEY, "string",
                "key2", mapper.createArrayNode(),
                "key3", TOO_LONG_VALUE
        );
        ExternalMetadata invalidExternalMetadata = new ExternalMetadata(metadata);
        Set<String> expectedErrorMessages = Set.of(
                "Field [metadata] values must be of type String, Boolean or Number",
                "Field [metadata] values must be no greater than 50 characters long",
                "Field [metadata] keys must be between 1 and 30 characters long");

        Set<ConstraintViolation<ExternalMetadata>> violations = validator.validate(invalidExternalMetadata);

        assertThat(violations.size(), is(3));
        assertThat(expectedErrorMessages.contains(violations.iterator().next().getMessage()), is(true));
        assertThat(expectedErrorMessages.contains(violations.iterator().next().getMessage()), is(true));
        assertThat(expectedErrorMessages.contains(violations.iterator().next().getMessage()), is(true));
    }
}
