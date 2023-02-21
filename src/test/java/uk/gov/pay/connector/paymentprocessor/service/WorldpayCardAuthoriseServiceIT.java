package uk.gov.pay.connector.paymentprocessor.service;

import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
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

}
