package uk.gov.pay.connector.app.validator;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.SqsConfig;

import javax.validation.ConstraintValidatorContext;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class SqsConfigValidatorTest {

    @Mock
    SqsConfig sqsConfig;
    @Mock
    ConstraintValidatorContext constraintValidatorContext;
    @Mock
    ConstraintValidatorContext.ConstraintViolationBuilder constraintViolationBuilder;

    SqsConfigValidator sqsConfigValidator = new SqsConfigValidator();

    @Before
    public void setUp() {
        when(constraintValidatorContext.buildConstraintViolationWithTemplate(anyString())).thenReturn(constraintViolationBuilder);
    }

    @Test
    public void shouldPassValidation_WhenNonServiceEndpointIsFalse() {
        when(sqsConfig.isNonStandardServiceEndpoint()).thenReturn(false);

        boolean isValid = sqsConfigValidator.isValid(sqsConfig, constraintValidatorContext);
        assertTrue(isValid);
    }

    @Test
    public void shouldPassValidation_WhenNonServiceEndpointIsTrueAndCredentialsAreAvailable() {
        when(sqsConfig.isNonStandardServiceEndpoint()).thenReturn(true);
        when(sqsConfig.getEndpoint()).thenReturn("http://endpoint");
        when(sqsConfig.getAccessKey()).thenReturn("access-key");
        when(sqsConfig.getSecretKey()).thenReturn("secret-key");

        boolean isValid = sqsConfigValidator.isValid(sqsConfig, constraintValidatorContext);

        verifyZeroInteractions(constraintValidatorContext.buildConstraintViolationWithTemplate(anyString()));
        assertTrue(isValid);
    }

    @Test
    public void shouldFailValidation_WhenNonServiceEndpointIsTrueAndEndpointIsNotAvailable() {
        when(sqsConfig.isNonStandardServiceEndpoint()).thenReturn(true);
        when(sqsConfig.getEndpoint()).thenReturn(null);

        boolean isValid = sqsConfigValidator.isValid(sqsConfig, constraintValidatorContext);

        verify(constraintValidatorContext).buildConstraintViolationWithTemplate("[endpoint, secretKey, accessKey] fields must be set, when `nonStandardServiceEndpoint` is true");
        assertFalse(isValid);
    }

    @Test
    public void shouldFailValidation_WhenNonServiceEndpointIsTrueAndSecretKeyIsNotAvailable() {
        when(sqsConfig.isNonStandardServiceEndpoint()).thenReturn(true);
        when(sqsConfig.getEndpoint()).thenReturn("http://endpoint");
        when(sqsConfig.getSecretKey()).thenReturn(null);

        boolean isValid = sqsConfigValidator.isValid(sqsConfig, constraintValidatorContext);

        verify(constraintValidatorContext).buildConstraintViolationWithTemplate("[endpoint, secretKey, accessKey] fields must be set, when `nonStandardServiceEndpoint` is true");
        assertFalse(isValid);
    }

    @Test
    public void shouldFailValidation_WhenNonServiceEndpointIsTrueAndAccessKeyIsNotAvailable() {
        when(sqsConfig.isNonStandardServiceEndpoint()).thenReturn(true);
        when(sqsConfig.getEndpoint()).thenReturn("http://endpoint");
        when(sqsConfig.getSecretKey()).thenReturn("secret-key");
        when(sqsConfig.getAccessKey()).thenReturn(null);

        boolean isValid = sqsConfigValidator.isValid(sqsConfig, constraintValidatorContext);

        verify(constraintValidatorContext).buildConstraintViolationWithTemplate("[endpoint, secretKey, accessKey] fields must be set, when `nonStandardServiceEndpoint` is true");
        assertFalse(isValid);
    }
}
