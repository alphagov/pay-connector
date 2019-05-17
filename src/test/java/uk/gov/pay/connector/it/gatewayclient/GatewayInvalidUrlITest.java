package uk.gov.pay.connector.it.gatewayclient;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.paymentprocessor.service.CardCaptureProcess;
import uk.gov.pay.connector.rules.GuiceAppWithPostgresAndSqsRule;

import static io.dropwizard.testing.ConfigOverride.config;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED_RETRY;

@RunWith(MockitoJUnitRunner.class)
public class GatewayInvalidUrlITest extends BaseGatewayITest {

    @Rule
    public GuiceAppWithPostgresAndSqsRule app = new GuiceAppWithPostgresAndSqsRule(
            config("smartpay.urls.test", "http://gobbledygook.invalid.url"));


    @Before
    public void setUp() {
        setupLoggerMockAppender();
    }

    @Test
    public void shouldFailCaptureWhenInvalidConnectorUrl() {
        DatabaseFixtures.TestCharge testCharge = createTestCharge(app.getDatabaseTestHelper());
        setupGatewayStub().respondWithUnexpectedResponseCodeWhenCapture();
        app.getInstanceFromGuiceContainer(CardCaptureProcess.class).loadCaptureQueue();
        app.getInstanceFromGuiceContainer(CardCaptureProcess.class).runCapture(1);

        assertLastGatewayClientLoggingEventContains("Exception for gateway url=http://gobbledygook.invalid.url");
        assertThat(app.getDatabaseTestHelper().getChargeStatus(testCharge.getChargeId()), is(CAPTURE_APPROVED_RETRY.getValue()));
    }
}
