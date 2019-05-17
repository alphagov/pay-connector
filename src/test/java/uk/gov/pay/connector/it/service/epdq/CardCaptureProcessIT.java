package uk.gov.pay.connector.it.service.epdq;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.it.service.CardCaptureProcessBaseITest;
import uk.gov.pay.connector.paymentprocessor.service.CardCaptureProcess;
import uk.gov.pay.connector.rules.EpdqMockClient;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED_RETRY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;

@RunWith(MockitoJUnitRunner.class)
public class CardCaptureProcessIT extends CardCaptureProcessBaseITest {

    private static final String PAYMENT_PROVIDER = "epdq";

    @Test
    public void shouldCapture() {
        DatabaseFixtures.TestCharge testCharge = createTestCharge(PAYMENT_PROVIDER, CAPTURE_APPROVED);

        new EpdqMockClient().mockCaptureSuccess();
        app.getInstanceFromGuiceContainer(CardCaptureProcess.class).loadCaptureQueue();
        app.getInstanceFromGuiceContainer(CardCaptureProcess.class).runCapture(1);

        assertThat(app.getDatabaseTestHelper().getChargeStatus(testCharge.getChargeId()), is(CAPTURE_SUBMITTED.getValue()));
    }

    @Test
    public void shouldNotCaptureWhenChargeIsNotInCorrectState() {
        DatabaseFixtures.TestCharge testCharge = createTestCharge(PAYMENT_PROVIDER, ENTERING_CARD_DETAILS);

        new EpdqMockClient().mockCaptureSuccess();
        app.getInstanceFromGuiceContainer(CardCaptureProcess.class).loadCaptureQueue();
        app.getInstanceFromGuiceContainer(CardCaptureProcess.class).runCapture(1);

        assertThat(app.getDatabaseTestHelper().getChargeStatus(testCharge.getChargeId()), is(ENTERING_CARD_DETAILS.getValue()));
    }

    @Test
    public void shouldNotCaptureWhenGatewayRepondsWithAnError() {
        DatabaseFixtures.TestCharge testCharge = createTestCharge(PAYMENT_PROVIDER, CAPTURE_APPROVED);

        new EpdqMockClient().mockCaptureError();
        app.getInstanceFromGuiceContainer(CardCaptureProcess.class).loadCaptureQueue();
        app.getInstanceFromGuiceContainer(CardCaptureProcess.class).runCapture(1);

        assertThat(app.getDatabaseTestHelper().getChargeStatus(testCharge.getChargeId()), is(CAPTURE_APPROVED_RETRY.getValue()));
    }

    @Test
    public void shouldNotCaptureWhenGatewayRepondsWithAnErrorAndAttemptsExceeded() {
        DatabaseFixtures.TestCharge testCharge = createTestCharge(PAYMENT_PROVIDER, CAPTURE_APPROVED);

        new EpdqMockClient().mockCaptureError();
        for (int i = 0; i < CAPTURE_MAX_RETRIES + 1; i++) {
            app.getInstanceFromGuiceContainer(CardCaptureProcess.class).loadCaptureQueue();
            app.getInstanceFromGuiceContainer(CardCaptureProcess.class).runCapture(1);
        }

        assertThat(app.getDatabaseTestHelper().getChargeStatus(testCharge.getChargeId()), is(CAPTURE_ERROR.getValue()));
    }
}
