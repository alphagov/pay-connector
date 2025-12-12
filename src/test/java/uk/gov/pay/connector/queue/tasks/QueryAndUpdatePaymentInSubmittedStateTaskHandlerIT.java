package uk.gov.pay.connector.queue.tasks;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.it.base.ITestBaseExtension;
import uk.gov.pay.connector.queue.tasks.handlers.QueryAndUpdatePaymentInSubmittedStateTaskHandler;
import uk.gov.pay.connector.queue.tasks.model.PaymentTaskData;
import uk.gov.pay.connector.util.RandomIdGenerator;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.util.AddChargeParams.AddChargeParamsBuilder.anAddChargeParams;
import static uk.gov.pay.connector.util.RandomGeneratorUtils.randomInt;

class QueryAndUpdatePaymentInSubmittedStateTaskHandlerIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    @RegisterExtension
    public static ITestBaseExtension testBaseExtension = new ITestBaseExtension("worldpay",
            app.getLocalPort(), app.getDatabaseTestHelper());

    @Test
    void shouldQueryAndUpdateChargeInSubmittedStateToCaptured() {
        long chargeId = randomInt();
        String chargeExternalId = RandomIdGenerator.newId();
        var paymentTaskData = new PaymentTaskData(chargeExternalId);

        app.getDatabaseTestHelper().addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(chargeExternalId)
                .withPaymentProvider(WORLDPAY.getName())
                .withGatewayAccountId(testBaseExtension.getAccountId())
                .withAmount(10000)
                .withStatus(ChargeStatus.CAPTURE_SUBMITTED)
                .withGatewayCredentialId(testBaseExtension.getCredentialParams().getId())
                .withTransactionId("gateway-tx-id-1")
                .build());

        app.getWorldpayMockClient().mockInquiryCaptured();

        QueryAndUpdatePaymentInSubmittedStateTaskHandler taskHandler =
                app.getInstanceFromGuiceContainer(QueryAndUpdatePaymentInSubmittedStateTaskHandler.class);
        taskHandler.process(paymentTaskData);

        Map<String, Object> charge = app.getDatabaseTestHelper().getChargeByExternalId(chargeExternalId);
        assertThat(charge.get("status").toString(), is(CAPTURED.getValue()));

        List<String> events = app.getDatabaseTestHelper().getInternalEvents(chargeExternalId);
        assertThat(events, hasItems(CAPTURED.getValue()));
    }
}
