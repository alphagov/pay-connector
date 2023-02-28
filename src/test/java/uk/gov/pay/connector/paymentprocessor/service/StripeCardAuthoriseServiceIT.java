package uk.gov.pay.connector.paymentprocessor.service;

import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.it.util.ChargeUtils;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;

import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.stripe.StripeAuthorisationResponse.STRIPE_RECURRING_AUTH_TOKEN_CUSTOMER_ID_KEY;
import static uk.gov.pay.connector.gateway.stripe.StripeAuthorisationResponse.STRIPE_RECURRING_AUTH_TOKEN_PAYMENT_METHOD_ID_KEY;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class StripeCardAuthoriseServiceIT extends ChargingITestBase {

    public StripeCardAuthoriseServiceIT() {
        super(STRIPE.getName());
    }

    @Test
    public void shouldSuccessfullyAuthoriseUserNotPresentPayment() {
        var recurringAuthToken = Map.of(
                STRIPE_RECURRING_AUTH_TOKEN_CUSTOMER_ID_KEY, "cus_abc123",
                STRIPE_RECURRING_AUTH_TOKEN_PAYMENT_METHOD_ID_KEY, "pm_abc123");
        ChargeUtils.ExternalChargeId userNotPresentChargeId = addChargeWithAuthorisationModeAgreement(recurringAuthToken);

        var successCharge = testContext.getInstanceFromGuiceContainer(ChargeService.class).findChargeByExternalId(userNotPresentChargeId.toString());

        stripeMockClient.mockCreatePaymentIntent();

        var successResponse = testContext.getInstanceFromGuiceContainer(CardAuthoriseService.class).doAuthoriseUserNotPresent(successCharge);
        assertThat(successResponse.getGatewayError(), is(Optional.empty()));
        assertThat(successResponse.getAuthoriseStatus(), is(Optional.of(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED)));
    }

}
