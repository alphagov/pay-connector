package uk.gov.pay.connector.card.service;

import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.charge.model.FirstDigitsCardNumber;
import uk.gov.pay.connector.charge.model.LastDigitsCardNumber;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.it.util.ChargeUtils;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;

import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class CardAuthoriseServiceIT extends ChargingITestBase {

    private static final LastDigitsCardNumber SANDBOX_SUCCESS_LAST_FOUR_DIGITS = LastDigitsCardNumber.of("4242");
    private static final FirstDigitsCardNumber SANDBOX_SUCCESS_FIRST_SIX_DIGITS = FirstDigitsCardNumber.of("424242");
    private static final LastDigitsCardNumber SANDBOX_DECLINE_LAST_FOUR_DIGITS = LastDigitsCardNumber.of("0002");
    private static final FirstDigitsCardNumber SANDBOX_DECLINE_FIRST_SIX_DIGITS = FirstDigitsCardNumber.of("400000");
    private static final LastDigitsCardNumber SANDBOX_SUCCESS_SETUP_DECLINE_RECURRING_LAST_FOUR_DIGITS = LastDigitsCardNumber.of("5100");
    private static final FirstDigitsCardNumber SANDBOX_SUCCESS_SETUP_DECLINE_RECURRING_FIRST_SIX_DIGITS = FirstDigitsCardNumber.of("510510");
    private static final String SANDBOX_RECURRING_FIRST_AUTHORISE_SUCCESS_SUBSEQUENT_DECLINE_CARD_NO = "5105105105105100";

    public CardAuthoriseServiceIT() {
        super("sandbox");
    }
    
    @Test
    public void shouldAuthoriseSandboxWebPayment() {
        String chargeExternalId = addCharge(ChargeStatus.ENTERING_CARD_DETAILS);
        var response = testContext.getInstanceFromGuiceContainer(CardAuthoriseService.class)
                .doAuthoriseWeb(chargeExternalId, AuthCardDetailsFixture.anAuthCardDetails().build());
        assertThat(response.getGatewayError(), is(Optional.empty()));
        assertThat(response.getAuthoriseStatus(), is(Optional.of(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED)));
    }

    @Test
    public void shouldSuccessfullyAuthoriseSandboxUserNotPresentPayment() {
        ChargeUtils.ExternalChargeId userNotPresentChargeId = addChargeWithAuthorisationModeAgreement(
                SANDBOX_SUCCESS_FIRST_SIX_DIGITS, SANDBOX_SUCCESS_LAST_FOUR_DIGITS, null);

        var successCharge = testContext.getInstanceFromGuiceContainer(ChargeService.class).findChargeByExternalId(userNotPresentChargeId.toString());

        var successResponse = testContext.getInstanceFromGuiceContainer(CardAuthoriseService.class).doAuthoriseUserNotPresent(successCharge);
        assertThat(successResponse.getGatewayError(), is(Optional.empty()));
        assertThat(successResponse.getAuthoriseStatus(), is(Optional.of(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED)));

        Map<String, Object> chargeUpdated = databaseTestHelper.getChargeByExternalId(userNotPresentChargeId.toString());
        assertThat(chargeUpdated.get("can_retry"), is(nullValue()));
    }

    @Test
    public void shouldDeclineAuthoriseSandboxUserNotPresentPayment() {
        ChargeUtils.ExternalChargeId userNotPresentChargeId = addChargeWithAuthorisationModeAgreement(SANDBOX_DECLINE_FIRST_SIX_DIGITS, SANDBOX_DECLINE_LAST_FOUR_DIGITS, null);

        var declineCharge = testContext.getInstanceFromGuiceContainer(ChargeService.class).findChargeByExternalId(userNotPresentChargeId.toString());

        var declineResponse = testContext.getInstanceFromGuiceContainer(CardAuthoriseService.class).doAuthoriseUserNotPresent(declineCharge);
        assertThat(declineResponse.getGatewayError(), is(Optional.empty()));
        assertThat(declineResponse.getAuthoriseStatus(), is(Optional.of(BaseAuthoriseResponse.AuthoriseStatus.REJECTED)));
    }

    @Test
    public void shouldAuthoriseRecurringSandboxAgreementSetUpAndDeclineSubsequentRecurringPayment() {
        String setupAgreementChargeId = addCharge(ChargeStatus.ENTERING_CARD_DETAILS);
        ChargeUtils.ExternalChargeId userNotPresentChargeId = addChargeWithAuthorisationModeAgreement(SANDBOX_SUCCESS_SETUP_DECLINE_RECURRING_FIRST_SIX_DIGITS, SANDBOX_SUCCESS_SETUP_DECLINE_RECURRING_LAST_FOUR_DIGITS, null);

        var chargeRecurring = testContext.getInstanceFromGuiceContainer(ChargeService.class).findChargeByExternalId(userNotPresentChargeId.toString());

        var agreementSetUpResponse = testContext.getInstanceFromGuiceContainer(CardAuthoriseService.class).doAuthoriseWeb(setupAgreementChargeId, AuthCardDetailsFixture.anAuthCardDetails().withCardNo(SANDBOX_RECURRING_FIRST_AUTHORISE_SUCCESS_SUBSEQUENT_DECLINE_CARD_NO).build());
        var recurringPaymentResponse = testContext.getInstanceFromGuiceContainer(CardAuthoriseService.class).doAuthoriseUserNotPresent(chargeRecurring);

        assertThat(agreementSetUpResponse.getGatewayError(), is(Optional.empty()));
        assertThat(agreementSetUpResponse.getAuthoriseStatus(), is(Optional.of(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED)));
        assertThat(recurringPaymentResponse.getGatewayError(), is(Optional.empty()));
        assertThat(recurringPaymentResponse.getAuthoriseStatus(), is(Optional.of(BaseAuthoriseResponse.AuthoriseStatus.REJECTED)));

        Map<String, Object> chargeUpdated = databaseTestHelper.getChargeByExternalId(userNotPresentChargeId.toString());
        assertThat(chargeUpdated.get("can_retry"), is(false));
    }

}
