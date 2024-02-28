package uk.gov.pay.connector.card.service;

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
import static org.hamcrest.Matchers.nullValue;
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

    @Test
    public void shouldProcessFailedAuthorisationForUserNotPresentPaymentAndSetCanRetryFieldToTrue() {
        var recurringAuthToken = Map.of(
                STRIPE_RECURRING_AUTH_TOKEN_CUSTOMER_ID_KEY, "cus_abc123",
                STRIPE_RECURRING_AUTH_TOKEN_PAYMENT_METHOD_ID_KEY, "pm_abc123");
        ChargeUtils.ExternalChargeId userNotPresentChargeId = addChargeWithAuthorisationModeAgreement(recurringAuthToken);

        var chargeEntity = testContext.getInstanceFromGuiceContainer(ChargeService.class).findChargeByExternalId(userNotPresentChargeId.toString());

        stripeMockClient.mockAuthorisationFailedPaymentIntentAndRetriableForUserNotPresentPayment();

        var authorisationResponse = testContext.getInstanceFromGuiceContainer(CardAuthoriseService.class).doAuthoriseUserNotPresent(chargeEntity);
        assertThat(authorisationResponse.getGatewayError(), is(Optional.empty()));
        assertThat(authorisationResponse.getAuthoriseStatus(), is(Optional.of(BaseAuthoriseResponse.AuthoriseStatus.REJECTED)));

        Map<String, Object> chargeUpdated = databaseTestHelper.getChargeByExternalId(userNotPresentChargeId.toString());

        assertThat(chargeUpdated.get("can_retry"), is(true));
    }

    @Test
    public void shouldProcessFailedAuthorisationForUserNotPresentPaymentAndSetCanRetryFieldToFalse() {
        var recurringAuthToken = Map.of(
                STRIPE_RECURRING_AUTH_TOKEN_CUSTOMER_ID_KEY, "cus_abc123",
                STRIPE_RECURRING_AUTH_TOKEN_PAYMENT_METHOD_ID_KEY, "pm_abc123");
        ChargeUtils.ExternalChargeId userNotPresentChargeId = addChargeWithAuthorisationModeAgreement(recurringAuthToken);

        var chargeEntity = testContext.getInstanceFromGuiceContainer(ChargeService.class).findChargeByExternalId(userNotPresentChargeId.toString());

        stripeMockClient.mockAuthorisationFailedPaymentIntentAndNonRetriableForUserNotPresentPayment();

        var authorisationResponse = testContext.getInstanceFromGuiceContainer(CardAuthoriseService.class).doAuthoriseUserNotPresent(chargeEntity);
        assertThat(authorisationResponse.getGatewayError(), is(Optional.empty()));
        assertThat(authorisationResponse.getAuthoriseStatus(), is(Optional.of(BaseAuthoriseResponse.AuthoriseStatus.REJECTED)));

        Map<String, Object> chargeUpdated = databaseTestHelper.getChargeByExternalId(userNotPresentChargeId.toString());

        assertThat(chargeUpdated.get("can_retry"), is(false));

        Map<String, Object> paymentInstrument = databaseTestHelper.getPaymentInstrumentByChargeExternalId(chargeEntity.getExternalId());
        assertThat(paymentInstrument.get("status"), is("INACTIVE"));
    }

    @Test
    public void shouldProcessFailedAuthorisationForUserNotPresentPaymentAndSetCanRetryFieldToNullForUnknownError() {
        var recurringAuthToken = Map.of(
                STRIPE_RECURRING_AUTH_TOKEN_CUSTOMER_ID_KEY, "cus_abc123",
                STRIPE_RECURRING_AUTH_TOKEN_PAYMENT_METHOD_ID_KEY, "pm_abc123");
        ChargeUtils.ExternalChargeId userNotPresentChargeId = addChargeWithAuthorisationModeAgreement(recurringAuthToken);

        var chargeEntity = testContext.getInstanceFromGuiceContainer(ChargeService.class).findChargeByExternalId(userNotPresentChargeId.toString());

        stripeMockClient.mockAuthorisationErrorForUserNotPresentPayment();

        var authorisationResponse = testContext.getInstanceFromGuiceContainer(CardAuthoriseService.class).doAuthoriseUserNotPresent(chargeEntity);
        assertThat(authorisationResponse.getGatewayError().get().getMessage(), is("There was an internal server error authorising charge"));
        assertThat(authorisationResponse.getAuthoriseStatus(), is(Optional.empty()));

        Map<String, Object> chargeUpdated = databaseTestHelper.getChargeByExternalId(userNotPresentChargeId.toString());

        assertThat(chargeUpdated.get("can_retry"), is(nullValue()));
    }
}
