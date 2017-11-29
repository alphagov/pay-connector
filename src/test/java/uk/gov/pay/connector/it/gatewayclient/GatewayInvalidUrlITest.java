package uk.gov.pay.connector.it.gatewayclient;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class GatewayInvalidUrlITest extends BaseGatewayITest {

    //FIXME: Unit test for this I think should be enough
    //@Rule
    //public GuiceAppWithPostgresRule app = new GuiceAppWithPostgresRule(
    //        config("smartpay.urls.test", "http://gobbledygook.invalid.url"));


    @Before
    public void setUp() throws Exception {
        setupLoggerMockAppender();
    }

   /* @Test
    public void shouldFailCaptureWhenInvalidConnectorUrl() throws Exception {
        DatabaseFixtures.TestCharge testCharge = createTestCharge(app.getDatabaseTestHelper());
        setupGatewayStub().respondWithUnexpectedResponseCodeWhenCapture();
        app.getInstanceFromGuiceContainer(CardCaptureProcess.class).runCapture();

        assertThatLastGatewayClientLoggingEventIs("DNS resolution error for gateway url=http://gobbledygook.invalid.url");
        Assert.assertThat(app.getDatabaseTestHelper().getChargeStatus(testCharge.getChargeId()), Matchers.is(CAPTURE_APPROVED_RETRY.getValue()));
    }*/
}
