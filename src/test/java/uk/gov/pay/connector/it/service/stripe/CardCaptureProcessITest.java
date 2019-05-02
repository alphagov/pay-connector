package uk.gov.pay.connector.it.service.stripe;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.it.service.CardCaptureProcessBaseITest;
import uk.gov.pay.connector.paymentprocessor.service.CardCaptureProcess;
import uk.gov.pay.connector.rules.StripeMockClient;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED_RETRY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;

@RunWith(MockitoJUnitRunner.class)
public class CardCaptureProcessITest extends CardCaptureProcessBaseITest {

    private static final String PAYMENT_PROVIDER = "stripe";

    @Test
    public void shouldCapture() {
        DatabaseFixtures.TestCharge testCharge = createTestCharge(PAYMENT_PROVIDER, CAPTURE_APPROVED);

        new StripeMockClient().mockCaptureSuccess(testCharge.getTransactionId());
        new StripeMockClient().mockTransferSuccess("transfer_out" + testCharge.getExternalChargeId());
        app.getInstanceFromGuiceContainer(CardCaptureProcess.class).loadCaptureQueue();
        app.getInstanceFromGuiceContainer(CardCaptureProcess.class).runCapture(1);
        
        verify(postRequestedFor(urlEqualTo("/v1/charges/" + testCharge.getTransactionId() + "/capture"))
                .withHeader("Idempotency-Key", equalTo("capture" + testCharge.getExternalChargeId())));
        
        verify(postRequestedFor(urlEqualTo("/v1/transfers"))
                .withHeader("Idempotency-Key", equalTo("transfer_out" + testCharge.getExternalChargeId()))
                .withRequestBody(containing("destination=stripe_account_id"))
                .withRequestBody(containing("source_transaction=" + testCharge.getTransactionId()))
                .withRequestBody(containing("amount=" + (testCharge.getAmount()))));

        Assert.assertThat(app.getDatabaseTestHelper().getChargeStatus(testCharge.getChargeId()), is(CAPTURED.getValue()));
    }

    @Test
    public void shouldNotCaptureWhenChargeIsNotInCorrectState() {
        DatabaseFixtures.TestCharge testCharge = createTestCharge(PAYMENT_PROVIDER, ENTERING_CARD_DETAILS);

        new StripeMockClient().mockCaptureSuccess(testCharge.getTransactionId());
        app.getInstanceFromGuiceContainer(CardCaptureProcess.class).runCapture(1);

        Assert.assertThat(app.getDatabaseTestHelper().getChargeStatus(testCharge.getChargeId()), is(ENTERING_CARD_DETAILS.getValue()));
    }

    @Test
    public void shouldNotCaptureWhenGatewayRepondsWithAnError() {
        DatabaseFixtures.TestCharge testCharge = createTestCharge(PAYMENT_PROVIDER, CAPTURE_APPROVED);

        new StripeMockClient().mockCaptureError(testCharge.getTransactionId());
        app.getInstanceFromGuiceContainer(CardCaptureProcess.class).loadCaptureQueue();
        app.getInstanceFromGuiceContainer(CardCaptureProcess.class).runCapture(1);

        Assert.assertThat(app.getDatabaseTestHelper().getChargeStatus(testCharge.getChargeId()), is(CAPTURE_APPROVED_RETRY.getValue()));
    }

    @Test
    public void shouldGoToCaptureRetry_whenNetTransferFails() {
        DatabaseFixtures.TestCharge testCharge = createTestCharge(PAYMENT_PROVIDER, CAPTURE_APPROVED);

        new StripeMockClient().mockCaptureSuccess(testCharge.getTransactionId());
        new StripeMockClient().mockTransferFailure();
        app.getInstanceFromGuiceContainer(CardCaptureProcess.class).loadCaptureQueue();
        app.getInstanceFromGuiceContainer(CardCaptureProcess.class).runCapture(1);

        Assert.assertThat(app.getDatabaseTestHelper().getChargeStatus(testCharge.getChargeId()), is(CAPTURE_APPROVED_RETRY.getValue()));
    }

    @Test
    public void shouldNotCaptureWhenGatewayRepondsWithAnErrorAndAttemptsExceeded() {
        DatabaseFixtures.TestCharge testCharge = createTestCharge(PAYMENT_PROVIDER, CAPTURE_APPROVED);

        new StripeMockClient().mockCaptureError(testCharge.getTransactionId());
        for (int i = 0; i < CAPTURE_MAX_RETRIES + 1; i++) {
            app.getInstanceFromGuiceContainer(CardCaptureProcess.class).loadCaptureQueue();
            app.getInstanceFromGuiceContainer(CardCaptureProcess.class).runCapture(1);
        }

        Assert.assertThat(app.getDatabaseTestHelper().getChargeStatus(testCharge.getChargeId()), is(CAPTURE_ERROR.getValue()));
    }
}
