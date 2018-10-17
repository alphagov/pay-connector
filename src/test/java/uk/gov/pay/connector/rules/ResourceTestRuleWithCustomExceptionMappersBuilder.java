package uk.gov.pay.connector.rules;

import io.dropwizard.jersey.errors.EarlyEofExceptionMapper;
import io.dropwizard.jersey.jackson.JsonProcessingExceptionMapper;
import io.dropwizard.testing.junit.ResourceTestRule;
import uk.gov.pay.connector.exception.ConstraintViolationExceptionMapper;
import uk.gov.pay.connector.exception.ValidationExceptionMapper;

public class ResourceTestRuleWithCustomExceptionMappersBuilder {
    public static ResourceTestRule.Builder getBuilder() {
        return ResourceTestRule.builder()
                .setRegisterDefaultExceptionMappers(false)
                .addProvider(ConstraintViolationExceptionMapper.class)
                .addProvider(ValidationExceptionMapper.class)
                .addProvider(JsonProcessingExceptionMapper.class)
                .addProvider(EarlyEofExceptionMapper.class);
    }
}
