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
    private final String SUCCESS_LAST_FOUR_DIGITS = "4242";
    private final String SUCCESS_FIRST_SIX_DIGITS = "424242";
    private final String DECLINE_LAST_FOUR_DIGITS = "0002";
    private final String DECLINE_FIRST_SIX_DIGITS = "400000";

    public CardAuthoriseServiceIT() {
        super("sandbox");
    }
    
    @Test
    public void shouldAuthoriseSandboxWebPayment() {
        addCharge("external-charge-id", null, null);
        var response = testContext.getInstanceFromGuiceContainer(CardAuthoriseService.class)
                .doAuthoriseWeb("external-charge-id", AuthCardDetailsFixture.anAuthCardDetails().build());
        assertThat(response.getGatewayError(), is(Optional.empty()));
        assertThat(response.getAuthoriseStatus(), is(Optional.of(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED)));
    }

    @Test
    public void shouldSuccessfullyAuthoriseSandboxUserNotPresentPayment() {
        addCharge("success-charge-external-id", SUCCESS_FIRST_SIX_DIGITS, SUCCESS_LAST_FOUR_DIGITS);

        var successCharge = testContext.getInstanceFromGuiceContainer(ChargeService.class).findChargeByExternalId("success-charge-external-id");

        var successResponse = testContext.getInstanceFromGuiceContainer(CardAuthoriseService.class).doAuthoriseUserNotPresent(successCharge);
        assertThat(successResponse.getGatewayError(), is(Optional.empty()));
        assertThat(successResponse.getAuthoriseStatus(), is(Optional.of(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED)));
    }

    @Test
    public void shouldDeclineAuthoriseSandboxUserNotPresentPayment() {
        addCharge("decline-charge-external-id", DECLINE_FIRST_SIX_DIGITS, DECLINE_LAST_FOUR_DIGITS);

        var declineCharge = testContext.getInstanceFromGuiceContainer(ChargeService.class).findChargeByExternalId("decline-charge-external-id");

        var declineResponse = testContext.getInstanceFromGuiceContainer(CardAuthoriseService.class).doAuthoriseUserNotPresent(declineCharge);
        assertThat(declineResponse.getGatewayError(), is(Optional.empty()));
        assertThat(declineResponse.getAuthoriseStatus(), is(Optional.of(BaseAuthoriseResponse.AuthoriseStatus.REJECTED)));
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
