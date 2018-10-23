package uk.gov.pay.connector.it.service.smartpay;

import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.it.service.CardCaptureProcessBaseITest;
import uk.gov.pay.connector.paymentprocessor.service.CardCaptureProcess;
import uk.gov.pay.connector.rules.SmartpayMockClient;

import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED_RETRY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;

@RunWith(MockitoJUnitRunner.class)
public class CardCaptureProcessITest extends CardCaptureProcessBaseITest {

    private static final String PAYMENT_PROVIDER = "smartpay";

    @Test
    public void shouldCapture() {
        DatabaseFixtures.TestCharge testCharge = createTestCharge(PAYMENT_PROVIDER, CAPTURE_APPROVED);

        new SmartpayMockClient().mockCaptureSuccess();
        app.getInstanceFromGuiceContainer(CardCaptureProcess.class).runCapture();

        Assert.assertThat(app.getDatabaseTestHelper().getChargeStatus(testCharge.getChargeId()), Matchers.is(CAPTURE_SUBMITTED.getValue()));
    }

    @Test
    public void shouldNotCaptureWhenChargeIsNotInCorrectState() {
        DatabaseFixtures.TestCharge testCharge = createTestCharge(PAYMENT_PROVIDER, ENTERING_CARD_DETAILS);

        new SmartpayMockClient().mockCaptureSuccess();
        app.getInstanceFromGuiceContainer(CardCaptureProcess.class).runCapture();

        Assert.assertThat(app.getDatabaseTestHelper().getChargeStatus(testCharge.getChargeId()), Matchers.is(ENTERING_CARD_DETAILS.getValue()));
    }

    @Test
    public void shouldNotCaptureWhenGatewayRepondsWithAnError() {
        DatabaseFixtures.TestCharge testCharge = createTestCharge(PAYMENT_PROVIDER, CAPTURE_APPROVED);

        new SmartpayMockClient().mockCaptureError();
        app.getInstanceFromGuiceContainer(CardCaptureProcess.class).runCapture();

        Assert.assertThat(app.getDatabaseTestHelper().getChargeStatus(testCharge.getChargeId()), Matchers.is(CAPTURE_APPROVED_RETRY.getValue()));
    }

    @Test
    public void shouldNotCaptureWhenGatewayRepondsWithAnErrorAndAttemptsExceeded() {
        DatabaseFixtures.TestCharge testCharge = createTestCharge(PAYMENT_PROVIDER, CAPTURE_APPROVED);

        new SmartpayMockClient().mockCaptureError();
        for (int i=0; i<CAPTURE_MAX_RETRIES+1; i++) {
            app.getInstanceFromGuiceContainer(CardCaptureProcess.class).runCapture();
        }

        Assert.assertThat(app.getDatabaseTestHelper().getChargeStatus(testCharge.getChargeId()), Matchers.is(CAPTURE_ERROR.getValue()));
    }
}
