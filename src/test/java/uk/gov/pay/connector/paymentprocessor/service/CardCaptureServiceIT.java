package uk.gov.pay.connector.paymentprocessor.service;


import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.fee.model.Fee;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.util.RandomIdGenerator;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.apache.commons.lang.math.RandomUtils.nextInt;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.charge.model.domain.FeeType.RADAR;
import static uk.gov.pay.connector.charge.model.domain.FeeType.THREE_D_S;
import static uk.gov.pay.connector.charge.model.domain.FeeType.TRANSACTION;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.util.AddChargeParams.AddChargeParamsBuilder.anAddChargeParams;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class CardCaptureServiceIT extends ChargingITestBase {

    public CardCaptureServiceIT() {
        super("stripe");
    }
    
    @Test
    public void shouldPersistFeesForStripeV2Charge() {
        long chargeId = nextInt();
        String externalChargeId = RandomIdGenerator.newId();

        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withPaymentProvider(STRIPE.getName())
                .withGatewayAccountId(accountId)
                .withAmount(10000)
                .withStatus(ChargeStatus.CAPTURE_READY)
                .build());

        String oldChargeStatus = ChargeStatus.CAPTURE_READY.getValue();
        String transactionId = "transactionId";
        List<Fee> feeList = List.of(Fee.of(RADAR, 10L), Fee.of(THREE_D_S, 20L), Fee.of(TRANSACTION, 480L));
        CaptureResponse captureResponse = new CaptureResponse(transactionId, CaptureResponse.ChargeState.COMPLETE, 100L, feeList);

        // Trigger the post gateway capture response programmatically which normally would be invoked by the scheduler.
        testContext.getInstanceFromGuiceContainer(CardCaptureService.class).processGatewayCaptureResponse(externalChargeId, oldChargeStatus, captureResponse);

        List<Map<String, Object>> listOfFees = databaseTestHelper.getFeesByChargeId(chargeId);
        List<String> feeTypeColumnValues = listOfFees.stream().map(map -> map.get("fee_type").toString()).collect(Collectors.toList());
        List<String> feeAmountColumnValues = listOfFees.stream().map(map -> map.get("amount_collected").toString()).collect(Collectors.toList());

        assertThat(listOfFees.size(), is(3));
        assertThat(feeTypeColumnValues, containsInAnyOrder("radar", "three_ds", "transaction"));
        assertThat(feeAmountColumnValues, containsInAnyOrder("10", "20", "480"));

    }
}
