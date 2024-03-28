package uk.gov.pay.connector.paymentprocessor.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.it.base.ChargingITestBaseExtension;
import uk.gov.pay.connector.it.util.ChargeUtils;

import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderStatusResponse.WORLDPAY_RECURRING_AUTH_TOKEN_PAYMENT_TOKEN_ID_KEY;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderStatusResponse.WORLDPAY_RECURRING_AUTH_TOKEN_TRANSACTION_IDENTIFIER_KEY;

public class WorldpayCardAuthoriseServiceIT {
    @RegisterExtension
    static ChargingITestBaseExtension app = new ChargingITestBaseExtension(WORLDPAY.getName());
    
    @Test
    void shouldSuccessfullyAuthoriseUserNotPresentPayment() {
        var recurringAuthToken = Map.of(
                WORLDPAY_RECURRING_AUTH_TOKEN_PAYMENT_TOKEN_ID_KEY, "a-token-id",
                WORLDPAY_RECURRING_AUTH_TOKEN_TRANSACTION_IDENTIFIER_KEY, "transaction-identifier");
        ChargeUtils.ExternalChargeId userNotPresentChargeId = app.addChargeWithAuthorisationModeAgreement(recurringAuthToken);

        var successCharge = app.getInstanceFromGuiceContainer(ChargeService.class).findChargeByExternalId(userNotPresentChargeId.toString());

        app.getWorldpayMockClient().mockAuthorisationSuccess();

        var successResponse = app.getInstanceFromGuiceContainer(CardAuthoriseService.class).doAuthoriseUserNotPresent(successCharge);
        assertThat(successResponse.getGatewayError(), is(Optional.empty()));
        assertThat(successResponse.getAuthoriseStatus(), is(Optional.of(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED)));
    }

    @Test
    void shouldProcessFailedAuthorisationForUserNotPresentPaymentAndSetCanRetryFieldToTrue() {
        var recurringAuthToken = Map.of(
                WORLDPAY_RECURRING_AUTH_TOKEN_PAYMENT_TOKEN_ID_KEY, "a-token-id",
                WORLDPAY_RECURRING_AUTH_TOKEN_TRANSACTION_IDENTIFIER_KEY, "transaction-identifier");
        ChargeUtils.ExternalChargeId userNotPresentChargeId = app.addChargeWithAuthorisationModeAgreement(recurringAuthToken);

        var chargeEntity = app.getInstanceFromGuiceContainer(ChargeService.class)
                .findChargeByExternalId(userNotPresentChargeId.toString());

        app.getWorldpayMockClient().mockAuthorisationFailure();

        var authorisationResponse = app.getInstanceFromGuiceContainer(CardAuthoriseService.class)
                .doAuthoriseUserNotPresent(chargeEntity);
        assertThat(authorisationResponse.getGatewayError(), is(Optional.empty()));
        assertThat(authorisationResponse.getAuthoriseStatus(), is(Optional.of(BaseAuthoriseResponse.AuthoriseStatus.REJECTED)));

        Map<String, Object> chargeUpdated = app.getDatabaseTestHelper().getChargeByExternalId(userNotPresentChargeId.toString());

        assertThat(chargeUpdated.get("can_retry"), is(true));
    }

    @Test
    void shouldProcessFailedAuthorisationForUserNotPresentPaymentAndSetCanRetryFieldToFalse() {
        var recurringAuthToken = Map.of(
                WORLDPAY_RECURRING_AUTH_TOKEN_PAYMENT_TOKEN_ID_KEY, "a-token-id",
                WORLDPAY_RECURRING_AUTH_TOKEN_TRANSACTION_IDENTIFIER_KEY, "transaction-identifier");
        ChargeUtils.ExternalChargeId userNotPresentChargeId = app.addChargeWithAuthorisationModeAgreement(recurringAuthToken);

        var chargeEntity = app.getInstanceFromGuiceContainer(ChargeService.class)
                .findChargeByExternalId(userNotPresentChargeId.toString());

        app.getWorldpayMockClient().mockAuthorisationFailureUserNotPresentNonRetriablePayment();

        var successResponse = app.getInstanceFromGuiceContainer(CardAuthoriseService.class)
                .doAuthoriseUserNotPresent(chargeEntity);
        assertThat(successResponse.getGatewayError(), is(Optional.empty()));
        assertThat(successResponse.getAuthoriseStatus(), is(Optional.of(BaseAuthoriseResponse.AuthoriseStatus.REJECTED)));

        Map<String, Object> chargeUpdated = app.getDatabaseTestHelper().getChargeByExternalId(userNotPresentChargeId.toString());

        assertThat(chargeUpdated.get("can_retry"), is(false));
    }

    @Test
    void shouldProcessFailedAuthorisationForUserNotPresentPaymentAndSetCanRetryFieldToNullForUnknownError() {
        var recurringAuthToken = Map.of(
                WORLDPAY_RECURRING_AUTH_TOKEN_PAYMENT_TOKEN_ID_KEY, "a-token-id",
                WORLDPAY_RECURRING_AUTH_TOKEN_TRANSACTION_IDENTIFIER_KEY, "transaction-identifier");
        ChargeUtils.ExternalChargeId userNotPresentChargeId = app.addChargeWithAuthorisationModeAgreement(recurringAuthToken);

        var chargeEntity = app.getInstanceFromGuiceContainer(ChargeService.class)
                .findChargeByExternalId(userNotPresentChargeId.toString());

        app.getWorldpayMockClient().mockAuthorisationGatewayError();

        var successResponse = app.getInstanceFromGuiceContainer(CardAuthoriseService.class)
                .doAuthoriseUserNotPresent(chargeEntity);
        assertThat(successResponse.getGatewayError().get().getMessage(), is("Non-success HTTP status code 404 from gateway"));
        assertThat(successResponse.getAuthoriseStatus(), is(Optional.empty()));

        Map<String, Object> chargeUpdated = app.getDatabaseTestHelper().getChargeByExternalId(userNotPresentChargeId.toString());

        assertThat(chargeUpdated.get("can_retry"), is(nullValue()));
    }
}
