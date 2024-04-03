package uk.gov.pay.connector.queue.tasks;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.it.base.ITestBaseExtension;
import uk.gov.pay.connector.queue.tasks.handlers.CollectFeesForFailedPaymentsTaskHandler;
import uk.gov.pay.connector.queue.tasks.model.PaymentTaskData;
import uk.gov.pay.connector.util.RandomIdGenerator;

import java.util.List;
import java.util.Map;

import static org.apache.commons.lang.math.RandomUtils.nextInt;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.util.AddChargeParams.AddChargeParamsBuilder.anAddChargeParams;

public class CollectFeesForFailedPaymentsTaskHandlerIT {
    @RegisterExtension
    static ITestBaseExtension app = new ITestBaseExtension("stripe");

    private String paymentIntentId = "stripe-payment-intent-id";

    @Test
    void shouldPersistFees() throws Exception {
        long chargeId = nextInt();
        String chargeExternalId = RandomIdGenerator.newId();
        var paymentTaskData = new PaymentTaskData(chargeExternalId);
        app.getDatabaseTestHelper().addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(chargeExternalId)
                .withPaymentProvider(STRIPE.getName())
                .withGatewayAccountId(app.getAccountId())
                .withAmount(10000)
                .withStatus(ChargeStatus.AUTHORISATION_REJECTED)
                .withGatewayCredentialId(app.getCredentialParams().getId())
                .withTransactionId(paymentIntentId)
                .build());

        app.getStripeMockClient().mockGet3DSAuthenticatedPaymentIntent(paymentIntentId);
        app.getStripeMockClient().mockTransferSuccess();

        CollectFeesForFailedPaymentsTaskHandler taskHandler = app.getInstanceFromGuiceContainer(CollectFeesForFailedPaymentsTaskHandler.class);
        taskHandler.collectAndPersistFees(paymentTaskData);

        List<Map<String, Object>> fees = app.getDatabaseTestHelper().getFeesByChargeId(chargeId);
        assertThat(fees, hasSize(2));
        assertThat(fees, containsInAnyOrder(
                allOf(
                        hasEntry("fee_type", (Object)"radar"),
                        hasEntry("amount_collected", 5L)
                ),
                allOf(
                        hasEntry("fee_type", (Object)"three_ds"),
                        hasEntry("amount_collected", 6L)
                )
        ));
    }
}
