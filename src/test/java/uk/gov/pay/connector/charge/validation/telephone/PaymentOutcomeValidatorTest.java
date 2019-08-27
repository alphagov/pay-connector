package uk.gov.pay.connector.charge.validation.telephone;

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.charge.model.telephone.PaymentOutcome;
import uk.gov.pay.connector.charge.model.telephone.Supplemental;
import uk.gov.pay.connector.charge.model.telephone.TelephoneChargeCreateRequest;
import uk.gov.pay.connector.rules.DropwizardAppRule;

import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import java.util.Set;

import static io.dropwizard.testing.ResourceHelpers.resourceFilePath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.util.NumberMatcher.isNumber;

public class PaymentOutcomeValidatorTest {

    @ClassRule
    public static final DropwizardAppRule<ConnectorConfiguration> app =
            new DropwizardAppRule<>(ConnectorApp.class, resourceFilePath("config/test-config.yaml"));

    private static TelephoneChargeCreateRequest.ChargeBuilder telephoneRequestBuilder = new TelephoneChargeCreateRequest.ChargeBuilder();

    private static Validator validator;

    @BeforeClass
    public static void setUpValidator() {
        validator = app.getEnvironment().getValidator();
        telephoneRequestBuilder
                .amount(1200L)
                .description("Some description")
                .reference("Some reference")
                .createdDate("2018-02-21T16:04:25Z")
                .authorisedDate("2018-02-21T16:05:33Z")
                .authCode("666")
                .processorId("1PROC")
                .providerId("1PROV")
                .cardExpiry("01/99")
                .cardType("visa")
                .lastFourDigits("1234")
                .firstSixDigits("123456")
                .nameOnCard("Jane Doe")
                .emailAddress("jane_doe@example.com")
                .telephoneNumber("+447700900796");
                
    }

    @Test
    public void failsValidationForInvalidPaymentOutcomeStatus() {

        TelephoneChargeCreateRequest telephoneChargeCreateRequest = telephoneRequestBuilder
                .paymentOutcome(new PaymentOutcome("invalid"))
                .build();

        Set<ConstraintViolation<TelephoneChargeCreateRequest>> constraintViolations = validator.validate(telephoneChargeCreateRequest);

        assertThat(constraintViolations.size(), isNumber(1));
        assertThat(constraintViolations.iterator().next().getMessage(), is("Must include a valid status and error code"));
    }

    @Test
    public void failsValidationForPaymentOutcomeStatusSuccessAndErrorCodeGiven() {

        PaymentOutcome paymentOutcome = new PaymentOutcome(
                "success",
                "error",
                new Supplemental(
                        "ECKOH01234",
                        "textual message describing error code"
                )
        );
        
        TelephoneChargeCreateRequest telephoneChargeCreateRequest = telephoneRequestBuilder
                .paymentOutcome(paymentOutcome)
                .build();

        Set<ConstraintViolation<TelephoneChargeCreateRequest>> constraintViolations = validator.validate(telephoneChargeCreateRequest);

        assertThat(constraintViolations.size(), isNumber(1));
        assertThat(constraintViolations.iterator().next().getMessage(), is("Must include a valid status and error code"));
    }
}
