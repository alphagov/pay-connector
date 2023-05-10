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
import static org.hamcrest.Matchers.nullValue;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderStatusResponse.WORLDPAY_RECURRING_AUTH_TOKEN_PAYMENT_TOKEN_ID_KEY;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderStatusResponse.WORLDPAY_RECURRING_AUTH_TOKEN_TRANSACTION_IDENTIFIER_KEY;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class WorldpayCardAuthoriseServiceIT extends ChargingITestBase {

    public WorldpayCardAuthoriseServiceIT() {
        super(WORLDPAY.getName());
    }

    @Test
    public void shouldSuccessfullyAuthoriseUserNotPresentPayment() {
        var recurringAuthToken = Map.of(
                WORLDPAY_RECURRING_AUTH_TOKEN_PAYMENT_TOKEN_ID_KEY, "a-token-id",
                WORLDPAY_RECURRING_AUTH_TOKEN_TRANSACTION_IDENTIFIER_KEY, "transaction-identifier");
        ChargeUtils.ExternalChargeId userNotPresentChargeId = addChargeWithAuthorisationModeAgreement(recurringAuthToken);

        var successCharge = testContext.getInstanceFromGuiceContainer(ChargeService.class).findChargeByExternalId(userNotPresentChargeId.toString());

        worldpayMockClient.mockAuthorisationSuccess();

        var successResponse = testContext.getInstanceFromGuiceContainer(CardAuthoriseService.class).doAuthoriseUserNotPresent(successCharge);
        assertThat(successResponse.getGatewayError(), is(Optional.empty()));
        assertThat(successResponse.getAuthoriseStatus(), is(Optional.of(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED)));
    }

    @Test
    public void shouldProcessFailedAuthorisationForUserNotPresentPaymentAndSetCanRetryFieldToTrue() {
        var recurringAuthToken = Map.of(
                WORLDPAY_RECURRING_AUTH_TOKEN_PAYMENT_TOKEN_ID_KEY, "a-token-id",
                WORLDPAY_RECURRING_AUTH_TOKEN_TRANSACTION_IDENTIFIER_KEY, "transaction-identifier");
        ChargeUtils.ExternalChargeId userNotPresentChargeId = addChargeWithAuthorisationModeAgreement(recurringAuthToken);

        var chargeEntity = testContext.getInstanceFromGuiceContainer(ChargeService.class)
                .findChargeByExternalId(userNotPresentChargeId.toString());

        worldpayMockClient.mockAuthorisationFailure();

        var authorisationResponse = testContext.getInstanceFromGuiceContainer(CardAuthoriseService.class)
                .doAuthoriseUserNotPresent(chargeEntity);
        assertThat(authorisationResponse.getGatewayError(), is(Optional.empty()));
        assertThat(authorisationResponse.getAuthoriseStatus(), is(Optional.of(BaseAuthoriseResponse.AuthoriseStatus.REJECTED)));

        Map<String, Object> chargeUpdated = databaseTestHelper.getChargeByExternalId(userNotPresentChargeId.toString());

        assertThat(chargeUpdated.get("can_retry"), is(true));
    }

    @Test
    public void shouldProcessFailedAuthorisationForUserNotPresentPaymentAndSetCanRetryFieldToFalse() {
        var recurringAuthToken = Map.of(
                WORLDPAY_RECURRING_AUTH_TOKEN_PAYMENT_TOKEN_ID_KEY, "a-token-id",
                WORLDPAY_RECURRING_AUTH_TOKEN_TRANSACTION_IDENTIFIER_KEY, "transaction-identifier");
        ChargeUtils.ExternalChargeId userNotPresentChargeId = addChargeWithAuthorisationModeAgreement(recurringAuthToken);

        var chargeEntity = testContext.getInstanceFromGuiceContainer(ChargeService.class)
                .findChargeByExternalId(userNotPresentChargeId.toString());

        worldpayMockClient.mockAuthorisationFailureUserNotPresentNonRetriablePayment();

        var successResponse = testContext.getInstanceFromGuiceContainer(CardAuthoriseService.class)
                .doAuthoriseUserNotPresent(chargeEntity);
        assertThat(successResponse.getGatewayError(), is(Optional.empty()));
        assertThat(successResponse.getAuthoriseStatus(), is(Optional.of(BaseAuthoriseResponse.AuthoriseStatus.REJECTED)));

        Map<String, Object> chargeUpdated = databaseTestHelper.getChargeByExternalId(userNotPresentChargeId.toString());

        assertThat(chargeUpdated.get("can_retry"), is(false));
    }

    @Test
    public void shouldProcessFailedAuthorisationForUserNotPresentPaymentAndSetCanRetryFieldToNullForUnknownError() {
        var recurringAuthToken = Map.of(
                WORLDPAY_RECURRING_AUTH_TOKEN_PAYMENT_TOKEN_ID_KEY, "a-token-id",
                WORLDPAY_RECURRING_AUTH_TOKEN_TRANSACTION_IDENTIFIER_KEY, "transaction-identifier");
        ChargeUtils.ExternalChargeId userNotPresentChargeId = addChargeWithAuthorisationModeAgreement(recurringAuthToken);

        var chargeEntity = testContext.getInstanceFromGuiceContainer(ChargeService.class)
                .findChargeByExternalId(userNotPresentChargeId.toString());

        worldpayMockClient.mockAuthorisationGatewayError();

        var successResponse = testContext.getInstanceFromGuiceContainer(CardAuthoriseService.class)
                .doAuthoriseUserNotPresent(chargeEntity);
        assertThat(successResponse.getGatewayError().get().getMessage(), is("Non-success HTTP status code 404 from gateway"));
        assertThat(successResponse.getAuthoriseStatus(), is(Optional.empty()));

        Map<String, Object> chargeUpdated = databaseTestHelper.getChargeByExternalId(userNotPresentChargeId.toString());

        assertThat(chargeUpdated.get("can_retry"), is(nullValue()));
    }
}
