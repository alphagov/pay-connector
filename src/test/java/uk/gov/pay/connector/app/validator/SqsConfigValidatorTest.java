package uk.gov.pay.connector.app.validator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.SqsConfig;

import jakarta.validation.ConstraintValidatorContext;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SqsConfigValidatorTest {

    @Mock
    SqsConfig sqsConfig;
    @Mock
    ConstraintValidatorContext constraintValidatorContext;
    @Mock
    ConstraintValidatorContext.ConstraintViolationBuilder constraintViolationBuilder;

    SqsConfigValidator sqsConfigValidator = new SqsConfigValidator();

    @Test
    void shouldPassValidation_WhenNonServiceEndpointIsFalse() {
        when(sqsConfig.isNonStandardServiceEndpoint()).thenReturn(false);

        boolean isValid = sqsConfigValidator.isValid(sqsConfig, constraintValidatorContext);
        assertTrue(isValid);
    }

    @Test
    void shouldPassValidation_WhenNonServiceEndpointIsTrueAndCredentialsAreAvailable() {
        when(constraintValidatorContext.buildConstraintViolationWithTemplate(anyString())).thenReturn(constraintViolationBuilder);

        when(sqsConfig.isNonStandardServiceEndpoint()).thenReturn(true);
        when(sqsConfig.getEndpoint()).thenReturn("http://endpoint");
        when(sqsConfig.getAccessKey()).thenReturn("access-key");
        when(sqsConfig.getSecretKey()).thenReturn("secret-key");

        boolean isValid = sqsConfigValidator.isValid(sqsConfig, constraintValidatorContext);

        verifyNoInteractions(constraintValidatorContext.buildConstraintViolationWithTemplate(anyString()));
        assertTrue(isValid);
    }

    @Test
    void shouldFailValidation_WhenNonServiceEndpointIsTrueAndEndpointIsNotAvailable() {
        when(constraintValidatorContext.buildConstraintViolationWithTemplate(anyString())).thenReturn(constraintViolationBuilder);

        when(sqsConfig.isNonStandardServiceEndpoint()).thenReturn(true);
        when(sqsConfig.getEndpoint()).thenReturn(null);

        boolean isValid = sqsConfigValidator.isValid(sqsConfig, constraintValidatorContext);

        verify(constraintValidatorContext).buildConstraintViolationWithTemplate("[endpoint, secretKey, accessKey] fields must be set, when `nonStandardServiceEndpoint` is true");
        assertFalse(isValid);
    }

    @Test
    void shouldFailValidation_WhenNonServiceEndpointIsTrueAndSecretKeyIsNotAvailable() {
        when(constraintValidatorContext.buildConstraintViolationWithTemplate(anyString())).thenReturn(constraintViolationBuilder);
        when(sqsConfig.isNonStandardServiceEndpoint()).thenReturn(true);
        when(sqsConfig.getEndpoint()).thenReturn("http://endpoint");
        when(sqsConfig.getSecretKey()).thenReturn(null);

        boolean isValid = sqsConfigValidator.isValid(sqsConfig, constraintValidatorContext);

        verify(constraintValidatorContext).buildConstraintViolationWithTemplate("[endpoint, secretKey, accessKey] fields must be set, when `nonStandardServiceEndpoint` is true");
        assertFalse(isValid);
    }

    @Test
    void shouldFailValidation_WhenNonServiceEndpointIsTrueAndAccessKeyIsNotAvailable() {
        when(constraintValidatorContext.buildConstraintViolationWithTemplate(anyString())).thenReturn(constraintViolationBuilder);
        when(sqsConfig.isNonStandardServiceEndpoint()).thenReturn(true);
        when(sqsConfig.getEndpoint()).thenReturn("http://endpoint");
        when(sqsConfig.getSecretKey()).thenReturn("secret-key");
        when(sqsConfig.getAccessKey()).thenReturn(null);

        boolean isValid = sqsConfigValidator.isValid(sqsConfig, constraintValidatorContext);

        verify(constraintValidatorContext).buildConstraintViolationWithTemplate("[endpoint, secretKey, accessKey] fields must be set, when `nonStandardServiceEndpoint` is true");
        assertFalse(isValid);
    }
}
