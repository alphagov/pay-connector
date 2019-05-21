package uk.gov.pay.connector.it.gatewayclient;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.paymentprocessor.service.CardCaptureProcess;
import uk.gov.pay.connector.rules.GuiceAppWithPostgresRule;

import static io.dropwizard.testing.ConfigOverride.config;
import static java.lang.String.format;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED_RETRY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;

@RunWith(MockitoJUnitRunner.class)
public class GatewayCaptureFailuresIT extends BaseGatewayITest {

    @Rule
    public GuiceAppWithPostgresRule app = new GuiceAppWithPostgresRule(
            config("smartpay.urls.test", "http://localhost:" + port + "/pal/servlet/soap/Payment"));

    @Before
    public void setUp() {
        setupLoggerMockAppender();
    }

    @Test
    public void shouldFailCaptureWhenUnexpectedResponseCodeFromGateway() {
        DatabaseFixtures.TestCharge testCharge = createTestCharge(app.getDatabaseTestHelper());
        setupGatewayStub().respondWithUnexpectedResponseCodeWhenCapture();
        app.getInstanceFromGuiceContainer(CardCaptureProcess.class).loadCaptureQueue();
        app.getInstanceFromGuiceContainer(CardCaptureProcess.class).runCapture(1);

        assertLastGatewayClientLoggingEventContains(
                format("Gateway returned unexpected status code: 999, for gateway url=http://localhost:%s/pal/servlet/soap/Payment with type test", port));
        assertThat(app.getDatabaseTestHelper().getChargeStatus(testCharge.getChargeId()), is(CAPTURE_APPROVED_RETRY.getValue()));
    }

    @Test
    public void shouldFailCaptureWhenMalformedResponseFromGateway() {
        DatabaseFixtures.TestCharge testCharge = createTestCharge(app.getDatabaseTestHelper());
        setupGatewayStub().respondWithMalformedBodyWhenCapture();
        app.getInstanceFromGuiceContainer(CardCaptureProcess.class).loadCaptureQueue();
        app.getInstanceFromGuiceContainer(CardCaptureProcess.class).runCapture(1);
        
        assertThat(app.getDatabaseTestHelper().getChargeStatus(testCharge.getChargeId()), is(CAPTURE_APPROVED_RETRY.getValue()));
    }

    @Test
    public void shouldSucceedCapture() {
        DatabaseFixtures.TestCharge testCharge = createTestCharge(app.getDatabaseTestHelper());
        setupGatewayStub().respondWithSuccessWhenCapture();
        app.getInstanceFromGuiceContainer(CardCaptureProcess.class).loadCaptureQueue();
        app.getInstanceFromGuiceContainer(CardCaptureProcess.class).runCapture(1);
        assertThat(app.getDatabaseTestHelper().getChargeStatus(testCharge.getChargeId()), is(CAPTURE_SUBMITTED.getValue()));
    }
}
