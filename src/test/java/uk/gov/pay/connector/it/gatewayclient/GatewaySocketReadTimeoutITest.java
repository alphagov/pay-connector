package uk.gov.pay.connector.it.gatewayclient;

import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.it.IntegrationWithGuiceEmulatorTestSuite;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.service.CardCaptureProcess;

import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_APPROVED_RETRY;

@RunWith(MockitoJUnitRunner.class)
public class GatewaySocketReadTimeoutITest extends BaseGatewayITest {

    @Before
    public void setUp() throws Exception {
        setupLoggerMockAppender();
    }

    @Test
    public void shouldFailCaptureWhenConnectionTimeoutFromGateway() throws Exception {
        DatabaseFixtures.TestCharge testCharge = createTestCharge(app.getDatabaseTestHelper());
        setupGatewayStub().respondWithTimeoutWhenCapture();
        app.getInstanceFromGuiceContainer(CardCaptureProcess.class).runCapture();

        assertThatLastGatewayClientLoggingEventIs(
                String.format("Connection timed out error for gateway url=http://localhost:%s/pal/servlet/soap/Payment", IntegrationWithGuiceEmulatorTestSuite.getExternalServicesPort()));
        Assert.assertThat(app.getDatabaseTestHelper().getChargeStatus(testCharge.getChargeId()), Matchers.is(CAPTURE_APPROVED_RETRY.getValue()));
    }
}
