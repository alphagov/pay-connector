package uk.gov.pay.connector.paymentprocessor.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.it.base.ITestBaseExtension;
import uk.gov.pay.connector.it.util.ChargeUtils;

import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.stripe.StripeAuthorisationResponse.STRIPE_RECURRING_AUTH_TOKEN_CUSTOMER_ID_KEY;
import static uk.gov.pay.connector.gateway.stripe.StripeAuthorisationResponse.STRIPE_RECURRING_AUTH_TOKEN_PAYMENT_METHOD_ID_KEY;

public class StripeCardAuthoriseServiceIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = AppWithPostgresAndSqsExtension.withPersistence();

    @RegisterExtension
    public static ITestBaseExtension testBaseExtension = new ITestBaseExtension("stripe", app.getLocalPort(), app.getDatabaseTestHelper());
    
    @Test
    void shouldSuccessfullyAuthoriseUserNotPresentPayment() {
        var recurringAuthToken = Map.of(
                STRIPE_RECURRING_AUTH_TOKEN_CUSTOMER_ID_KEY, "cus_abc123",
                STRIPE_RECURRING_AUTH_TOKEN_PAYMENT_METHOD_ID_KEY, "pm_abc123");
        ChargeUtils.ExternalChargeId userNotPresentChargeId = testBaseExtension.addChargeWithAuthorisationModeAgreement(recurringAuthToken);

        var successCharge = app.getInstanceFromGuiceContainer(ChargeService.class).findChargeByExternalId(userNotPresentChargeId.toString());

        app.getStripeMockClient().mockCreatePaymentIntent();

        var successResponse = app.getInstanceFromGuiceContainer(CardAuthoriseService.class).doAuthoriseUserNotPresent(successCharge);
        assertThat(successResponse.getGatewayError(), is(Optional.empty()));
        assertThat(successResponse.getAuthoriseStatus(), is(Optional.of(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED)));
    }

    @Test
    void shouldProcessFailedAuthorisationForUserNotPresentPaymentAndSetCanRetryFieldToTrue() {
        var recurringAuthToken = Map.of(
                STRIPE_RECURRING_AUTH_TOKEN_CUSTOMER_ID_KEY, "cus_abc123",
                STRIPE_RECURRING_AUTH_TOKEN_PAYMENT_METHOD_ID_KEY, "pm_abc123");
        ChargeUtils.ExternalChargeId userNotPresentChargeId = testBaseExtension.addChargeWithAuthorisationModeAgreement(recurringAuthToken);

        var chargeEntity = app.getInstanceFromGuiceContainer(ChargeService.class).findChargeByExternalId(userNotPresentChargeId.toString());

        app.getStripeMockClient().mockAuthorisationFailedPaymentIntentAndRetriableForUserNotPresentPayment();

        var authorisationResponse = app.getInstanceFromGuiceContainer(CardAuthoriseService.class).doAuthoriseUserNotPresent(chargeEntity);
        assertThat(authorisationResponse.getGatewayError(), is(Optional.empty()));
        assertThat(authorisationResponse.getAuthoriseStatus(), is(Optional.of(BaseAuthoriseResponse.AuthoriseStatus.REJECTED)));

        Map<String, Object> chargeUpdated = app.getDatabaseTestHelper().getChargeByExternalId(userNotPresentChargeId.toString());

        assertThat(chargeUpdated.get("can_retry"), is(true));
    }

    @Test
    void shouldProcessFailedAuthorisationForUserNotPresentPaymentAndSetCanRetryFieldToFalse() {
        var recurringAuthToken = Map.of(
                STRIPE_RECURRING_AUTH_TOKEN_CUSTOMER_ID_KEY, "cus_abc123",
                STRIPE_RECURRING_AUTH_TOKEN_PAYMENT_METHOD_ID_KEY, "pm_abc123");
        ChargeUtils.ExternalChargeId userNotPresentChargeId = testBaseExtension.addChargeWithAuthorisationModeAgreement(recurringAuthToken);

        var chargeEntity = app.getInstanceFromGuiceContainer(ChargeService.class).findChargeByExternalId(userNotPresentChargeId.toString());

        app.getStripeMockClient().mockAuthorisationFailedPaymentIntentAndNonRetriableForUserNotPresentPayment();

        var authorisationResponse = app.getInstanceFromGuiceContainer(CardAuthoriseService.class).doAuthoriseUserNotPresent(chargeEntity);
        assertThat(authorisationResponse.getGatewayError(), is(Optional.empty()));
        assertThat(authorisationResponse.getAuthoriseStatus(), is(Optional.of(BaseAuthoriseResponse.AuthoriseStatus.REJECTED)));

        Map<String, Object> chargeUpdated = app.getDatabaseTestHelper().getChargeByExternalId(userNotPresentChargeId.toString());

        assertThat(chargeUpdated.get("can_retry"), is(false));

        Map<String, Object> paymentInstrument = app.getDatabaseTestHelper().getPaymentInstrumentByChargeExternalId(chargeEntity.getExternalId());
        assertThat(paymentInstrument.get("status"), is("INACTIVE"));
    }

    @Test
    void shouldProcessFailedAuthorisationForUserNotPresentPaymentAndSetCanRetryFieldToNullForUnknownError() {
        var recurringAuthToken = Map.of(
                STRIPE_RECURRING_AUTH_TOKEN_CUSTOMER_ID_KEY, "cus_abc123",
                STRIPE_RECURRING_AUTH_TOKEN_PAYMENT_METHOD_ID_KEY, "pm_abc123");
        ChargeUtils.ExternalChargeId userNotPresentChargeId = testBaseExtension.addChargeWithAuthorisationModeAgreement(recurringAuthToken);

        var chargeEntity = app.getInstanceFromGuiceContainer(ChargeService.class).findChargeByExternalId(userNotPresentChargeId.toString());

        app.getStripeMockClient().mockAuthorisationErrorForUserNotPresentPayment();

        var authorisationResponse = app.getInstanceFromGuiceContainer(CardAuthoriseService.class).doAuthoriseUserNotPresent(chargeEntity);
        assertThat(authorisationResponse.getGatewayError().get().getMessage(), is("There was an internal server error authorising charge"));
        assertThat(authorisationResponse.getAuthoriseStatus(), is(Optional.empty()));

        Map<String, Object> chargeUpdated = app.getDatabaseTestHelper().getChargeByExternalId(userNotPresentChargeId.toString());

        assertThat(chargeUpdated.get("can_retry"), is(nullValue()));
    }
}
