package uk.gov.pay.connector.rules;

import io.dropwizard.jersey.errors.EarlyEofExceptionMapper;
import io.dropwizard.jersey.jackson.JsonProcessingExceptionMapper;
import io.dropwizard.testing.junit5.ResourceExtension;
import uk.gov.pay.connector.common.exception.ConstraintViolationExceptionMapper;
import uk.gov.pay.connector.common.exception.ValidationExceptionMapper;

public class ResourceTestRuleWithCustomExceptionMappersBuilder {
    public static ResourceExtension.Builder getBuilder() {
        return ResourceExtension.builder()
                .setRegisterDefaultExceptionMappers(false)
                .addProvider(ConstraintViolationExceptionMapper.class)
                .addProvider(ValidationExceptionMapper.class)
                .addProvider(JsonProcessingExceptionMapper.class)
                .addProvider(EarlyEofExceptionMapper.class);
    }
}
