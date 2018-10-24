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

@RunWith(MockitoJUnitRunner.class)
public class GatewayInvalidUrlITest extends BaseGatewayITest {

    @Rule
    public GuiceAppWithPostgresRule app = new GuiceAppWithPostgresRule(
            config("smartpay.urls.test", "http://gobbledygook.invalid.url"));


    @Before
    public void setUp() {
        setupLoggerMockAppender();
    }

    @Test
    public void shouldFailCaptureWhenInvalidConnectorUrl() {
        DatabaseFixtures.TestCharge testCharge = createTestCharge(app.getDatabaseTestHelper());
        setupGatewayStub().respondWithUnexpectedResponseCodeWhenCapture();
        app.getInstanceFromGuiceContainer(CardCaptureProcess.class).runCapture();

        assertThatLastGatewayClientLoggingEventIs("DNS resolution error for gateway url=http://gobbledygook.invalid.url");
        Assert.assertThat(app.getDatabaseTestHelper().getChargeStatus(testCharge.getChargeId()), Matchers.is(CAPTURE_APPROVED_RETRY.getValue()));
    }
}
