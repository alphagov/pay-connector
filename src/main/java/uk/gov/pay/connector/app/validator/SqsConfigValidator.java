package uk.gov.pay.connector.app.validator;

import uk.gov.pay.connector.app.SqsConfig;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

public class SqsConfigValidator
        implements ConstraintValidator<ValidSqsConfig, SqsConfig> {

    @Override
    public boolean isValid(SqsConfig sqsConfig, ConstraintValidatorContext constraintValidatorContext) {

        if (sqsConfig.isNonStandardServiceEndpoint()) {
            boolean validEndpointConfig = isNotEmpty(sqsConfig.getEndpoint())
                    && isNotEmpty(sqsConfig.getSecretKey())
                    && isNotEmpty(sqsConfig.getAccessKey());

            if (!validEndpointConfig) {
                constraintValidatorContext.disableDefaultConstraintViolation();
                constraintValidatorContext
                        .buildConstraintViolationWithTemplate("[endpoint, secretKey, accessKey] fields must be set, when `nonStandardServiceEndpoint` is true ")
                        .addConstraintViolation();
            }
            return validEndpointConfig;
        }
        return true;
    }
}
