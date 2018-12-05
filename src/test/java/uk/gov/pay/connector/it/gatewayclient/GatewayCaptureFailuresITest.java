package uk.gov.pay.connector.it.gatewayclient;

import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.paymentprocessor.service.CardCaptureProcess;
import uk.gov.pay.connector.rules.GuiceAppWithPostgresRule;

import static io.dropwizard.testing.ConfigOverride.config;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED_RETRY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;

@RunWith(MockitoJUnitRunner.class)
public class GatewayCaptureFailuresITest extends BaseGatewayITest {

    @Rule
    public GuiceAppWithPostgresRule app = new GuiceAppWithPostgresRule(
            config("smartpay.urls.test", "http://localhost:" + port + "/pal/servlet/soap/Payment"));

    @Before
    public void setUp() throws Exception {
        setupLoggerMockAppender();
    }

    @Test
    public void shouldFailCaptureWhenUnexpectedResponseCodeFromGateway() {
        DatabaseFixtures.TestCharge testCharge = createTestCharge(app.getDatabaseTestHelper());
        setupGatewayStub().respondWithUnexpectedResponseCodeWhenCapture();
        app.getInstanceFromGuiceContainer(CardCaptureProcess.class).runCapture();

        assertThatLastGatewayClientLoggingEventIs(
                String.format("Gateway returned unexpected status code: 999, for gateway url=http://localhost:%s/pal/servlet/soap/Payment with type test", port));
        Assert.assertThat(app.getDatabaseTestHelper().getChargeStatus(testCharge.getChargeId()), Matchers.is(CAPTURE_APPROVED_RETRY.getValue()));
    }

    @Test
    public void shouldFailCaptureWhenMalformedResponseFromGateway() {
        DatabaseFixtures.TestCharge testCharge = createTestCharge(app.getDatabaseTestHelper());
        setupGatewayStub().respondWithMalformedBody_WhenCapture();
        app.getInstanceFromGuiceContainer(CardCaptureProcess.class).runCapture();

        assertThatLastGatewayClientLoggingEventIs("Could not unmarshall response >>>|<malformed xml/>|<<<.");
        Assert.assertThat(app.getDatabaseTestHelper().getChargeStatus(testCharge.getChargeId()), Matchers.is(CAPTURE_APPROVED_RETRY.getValue()));
    }

    @Test
    public void shouldSucceedCapture() {
        DatabaseFixtures.TestCharge testCharge = createTestCharge(app.getDatabaseTestHelper());
        setupGatewayStub().respondWithSuccessWhenCapture();
        app.getInstanceFromGuiceContainer(CardCaptureProcess.class).runCapture();
        Assert.assertThat(app.getDatabaseTestHelper().getChargeStatus(testCharge.getChargeId()), Matchers.is(CAPTURE_SUBMITTED.getValue()));
    }
}
