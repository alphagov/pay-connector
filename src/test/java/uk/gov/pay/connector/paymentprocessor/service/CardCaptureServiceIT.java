package uk.gov.pay.connector.paymentprocessor.service;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.fee.model.Fee;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.it.base.ITestBaseExtension;
import uk.gov.pay.connector.util.RandomIdGenerator;

import java.util.List;
import java.util.Map;

import static java.util.concurrent.ThreadLocalRandom.current;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static uk.gov.pay.connector.charge.model.domain.FeeType.RADAR;
import static uk.gov.pay.connector.charge.model.domain.FeeType.THREE_D_S;
import static uk.gov.pay.connector.charge.model.domain.FeeType.TRANSACTION;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.util.AddChargeParams.AddChargeParamsBuilder.anAddChargeParams;

public class CardCaptureServiceIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    @RegisterExtension
    public static ITestBaseExtension testBaseExtension = new ITestBaseExtension("stripe", app.getLocalPort(), app.getDatabaseTestHelper());

    @Test
    void shouldPersistFeesForStripeV2Charge() {
        long chargeId = current().nextInt(0, Integer.MAX_VALUE);
        String externalChargeId = RandomIdGenerator.newId();

        app.getDatabaseTestHelper().addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withPaymentProvider(STRIPE.getName())
                .withGatewayAccountId(testBaseExtension.getAccountId())
                .withAmount(10000)
                .withStatus(ChargeStatus.CAPTURE_READY)
                .build());

        String oldChargeStatus = ChargeStatus.CAPTURE_READY.getValue();
        String transactionId = "transactionId";
        List<Fee> feeList = List.of(Fee.of(RADAR, 10L), Fee.of(THREE_D_S, 20L), Fee.of(TRANSACTION, 480L));
        CaptureResponse captureResponse = new CaptureResponse(transactionId, CaptureResponse.ChargeState.COMPLETE, feeList);

        // Trigger the post gateway capture response programmatically which normally would be invoked by the scheduler.
        app.getInstanceFromGuiceContainer(CardCaptureService.class).processGatewayCaptureResponse(externalChargeId, oldChargeStatus, captureResponse);

        List<Map<String, Object>> listOfFees = app.getDatabaseTestHelper().getFeesByChargeId(chargeId);

        assertThat(listOfFees, hasSize(3));
        assertThat(listOfFees, containsInAnyOrder(
                allOf(
                        hasEntry("fee_type", (Object) "radar"),
                        hasEntry("amount_collected", 10L)
                ),
                allOf(
                        hasEntry("fee_type", (Object) "three_ds"),
                        hasEntry("amount_collected", 20L)
                ),
                allOf(
                        hasEntry("fee_type", (Object) "transaction"),
                        hasEntry("amount_collected", 480L)
                )
        ));

    }
}
