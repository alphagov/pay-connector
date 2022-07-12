package uk.gov.pay.connector.paymentprocessor.service;

import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.charge.model.FirstDigitsCardNumber;
import uk.gov.pay.connector.charge.model.LastDigitsCardNumber;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;
import uk.gov.service.payments.commons.model.AuthorisationMode;

import java.util.Optional;

import static org.apache.commons.lang.math.RandomUtils.nextInt;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.SANDBOX;
import static uk.gov.pay.connector.util.AddChargeParams.AddChargeParamsBuilder.anAddChargeParams;
import static uk.gov.pay.connector.util.AddPaymentInstrumentParams.AddPaymentInstrumentParamsBuilder.anAddPaymentInstrumentParams;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class CardAuthoriseServiceIT extends ChargingITestBase {

    public CardAuthoriseServiceIT() {
        super("sandbox");
    }
    
    @Test
    public void shouldAuthoriseSandboxWebPayment() {
        addWebCharge("external-charge-id");
        var response = testContext.getInstanceFromGuiceContainer(CardAuthoriseService.class)
                .doAuthoriseWeb("external-charge-id", AuthCardDetailsFixture.anAuthCardDetails().build());
        assertThat(response.getGatewayError(), is(Optional.empty()));
        assertThat(response.getAuthoriseStatus(), is(Optional.of(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED)));
    }

    @Test
    public void shouldSuccessfullyAuthoriseSandboxUserNotPresentPayment() {
        String SUCCESS_LAST_FOUR_DIGITS = "4242";
        String SUCCESS_FIRST_SIX_DIGITS = "424242";
        addAgreementCharge("success-charge-external-id", SUCCESS_FIRST_SIX_DIGITS, SUCCESS_LAST_FOUR_DIGITS);

        var successCharge = testContext.getInstanceFromGuiceContainer(ChargeService.class).findChargeByExternalId("success-charge-external-id");

        var successResponse = testContext.getInstanceFromGuiceContainer(CardAuthoriseService.class).doAuthoriseUserNotPresent(successCharge);
        assertThat(successResponse.getGatewayError(), is(Optional.empty()));
        assertThat(successResponse.getAuthoriseStatus(), is(Optional.of(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED)));
    }

    @Test
    public void shouldDeclineAuthoriseSandboxUserNotPresentPayment() {
        String DECLINE_LAST_FOUR_DIGITS = "0002";
        String DECLINE_FIRST_SIX_DIGITS = "400000";
        addAgreementCharge("decline-charge-external-id", DECLINE_FIRST_SIX_DIGITS, DECLINE_LAST_FOUR_DIGITS);

        var declineCharge = testContext.getInstanceFromGuiceContainer(ChargeService.class).findChargeByExternalId("decline-charge-external-id");

        var declineResponse = testContext.getInstanceFromGuiceContainer(CardAuthoriseService.class).doAuthoriseUserNotPresent(declineCharge);
        assertThat(declineResponse.getGatewayError(), is(Optional.empty()));
        assertThat(declineResponse.getAuthoriseStatus(), is(Optional.of(BaseAuthoriseResponse.AuthoriseStatus.REJECTED)));
    }

    @Test
    public void shouldAuthoriseRecurringSandboxAgreementSetupAndDeclineSubsequentRecurringPayment() {
        addWebCharge("setup-external-id");
        String SUCCESS_SETUP_DECLINE_RECURRING_LAST_FOUR_DIGITS = "5100";
        String SUCCESS_SETUP_DECLINE_RECURRING_FIRST_SIX_DIGITS = "510510";

        addAgreementCharge("recurring-external-id", SUCCESS_SETUP_DECLINE_RECURRING_FIRST_SIX_DIGITS, SUCCESS_SETUP_DECLINE_RECURRING_LAST_FOUR_DIGITS);

        var chargeRecurring = testContext.getInstanceFromGuiceContainer(ChargeService.class).findChargeByExternalId("recurring-external-id");

        String RECURRING_FIRST_AUTHORISE_SUCCESS_SUBSEQUENT_DECLINE = "5105105105105100";
        var agreementSetupResponse = testContext.getInstanceFromGuiceContainer(CardAuthoriseService.class).doAuthoriseWeb("setup-external-id", AuthCardDetailsFixture.anAuthCardDetails().withCardNo(RECURRING_FIRST_AUTHORISE_SUCCESS_SUBSEQUENT_DECLINE).build());
        var recurringPaymentResponse = testContext.getInstanceFromGuiceContainer(CardAuthoriseService.class).doAuthoriseUserNotPresent(chargeRecurring);

        assertThat(agreementSetupResponse.getGatewayError(), is(Optional.empty()));
        assertThat(agreementSetupResponse.getAuthoriseStatus(), is(Optional.of(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED)));
        assertThat(recurringPaymentResponse.getGatewayError(), is(Optional.empty()));
        assertThat(recurringPaymentResponse.getAuthoriseStatus(), is(Optional.of(BaseAuthoriseResponse.AuthoriseStatus.REJECTED)));
    }

    private void addWebCharge(String externalId) {
        addCharge(externalId, null, null);
    }

    private void addAgreementCharge(String externalId, String first6DigitsCardNumber, String last4DigitsCardNumber) {
        addCharge(externalId, first6DigitsCardNumber, last4DigitsCardNumber);
    }

    private void addCharge(String externalId, String first6DigitsCardNumber, String last4DigitsCardNumber) {
        var chargeParams = anAddChargeParams()
                .withExternalChargeId(externalId)
                .withPaymentProvider(SANDBOX.getName())
                .withGatewayAccountId(accountId)
                .withAmount(10000)
                .withStatus(ChargeStatus.ENTERING_CARD_DETAILS);

        if (last4DigitsCardNumber != null) {
            long paymentInstrumentId = nextInt();
            databaseTestHelper.addPaymentInstrument(
                    anAddPaymentInstrumentParams()
                            .withPaymentInstrumentId(paymentInstrumentId)
                            .withExternalPaymentInstrumentId(String.valueOf(nextInt()))
                            .withFirstDigitsCardNumber(FirstDigitsCardNumber.of(first6DigitsCardNumber))
                            .withLastDigitsCardNumber(LastDigitsCardNumber.of(last4DigitsCardNumber))
                            .build()
            );
            chargeParams
                   .withStatus(ChargeStatus.CREATED)
                   .withAuthorisationMode(AuthorisationMode.AGREEMENT)
                   .withPaymentInstrumentId(paymentInstrumentId);
        }
        databaseTestHelper.addCharge(chargeParams.build());
    }
}
