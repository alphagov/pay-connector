package uk.gov.pay.connector.it.service.sandbox;

import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.it.service.CardCaptureProcessBaseITest;
import uk.gov.pay.connector.rules.WorldpayMockClient;
import uk.gov.pay.connector.service.CardCaptureProcess;

import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.*;

@RunWith(MockitoJUnitRunner.class)
public class CardCaptureProcessITest extends CardCaptureProcessBaseITest {

    private static final String PAYMENT_PROVIDER = "sandbox";

    @Test
    public void shouldCapture() {
        DatabaseFixtures.TestCharge testCharge = createTestCharge(PAYMENT_PROVIDER, CAPTURE_APPROVED);

        new WorldpayMockClient().mockCaptureSuccess();
        app.getInstanceFromGuiceContainer(CardCaptureProcess.class).runCapture();

        Assert.assertThat(app.getDatabaseTestHelper().getChargeStatus(testCharge.getChargeId()), Matchers.is(CAPTURED.getValue()));
    }

    @Test
    public void shouldNotCaptureWhenChargeIsNotInCorrectState() {
        DatabaseFixtures.TestCharge testCharge = createTestCharge(PAYMENT_PROVIDER, ENTERING_CARD_DETAILS);

        new WorldpayMockClient().mockCaptureSuccess();
        app.getInstanceFromGuiceContainer(CardCaptureProcess.class).runCapture();

        Assert.assertThat(app.getDatabaseTestHelper().getChargeStatus(testCharge.getChargeId()), Matchers.is(ENTERING_CARD_DETAILS.getValue()));
    }
}
