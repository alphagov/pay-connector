package uk.gov.pay.connector.service;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.notifications.client.api.GovNotifyClientException;
import uk.gov.notifications.client.model.StatusResponse;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.it.mock.NotifyEmailMock;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.util.PortFactory;

import java.util.Optional;

import static io.dropwizard.testing.ConfigOverride.config;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class UserNotificationServiceIntTest {

    private final int port = PortFactory.findFreePort();

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(port);
    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule(
            config("notifyConfig.notificationBaseURL", "http://localhost:" + port),
            config("notifyConfig.emailNotifyEnabled", "true")
    );

    private UserNotificationService userNotificationService;
    private NotifyEmailMock notifyEmailMock = new NotifyEmailMock();

    private static final String SUCCESS_EMAIL_REQUEST_RESPONSE = "{" +
            "                        \"data\":{" +
            "                            \"notification\":{" +
            "                            \"id\": \"100\"" +
            "                            }," +
            "                            \"body\": \"this is the template with personalisation:\"," +
            "                            \"template_version\": 1," +
            "                            \"subject\": \"a-subject\"" +
            "                            }" +
            "                        };";

    private static final String BAD_REQUEST_RESPONSE = "{" +
            "\"result\": \"error\", " +
            "\"message\": \"id: required field\"}";

    private static final String SUCCESS_EMAIL_DELIVERY_RESPONSE = "{" +
            "  \"data\": {" +
            "    \"notification\": {" +
            "      \"status\": \"delivered\"," +
            "      \"to\": \"email@mock.com\"," +
            "      \"template\": {" +
            "        \"id\": \"template-id\"," +
            "        \"name\": \"First template\"," +
            "        \"template_type\": \"email\"" +
            "      }," +
            "      \"created_at\": \"2016-07-18T15:34:27.474Z\"," +
            "      \"updated_at\": \"2016-07-18T15:34:27.474Z\"," +
            "      \"sent_at\": \"2016-07-18T15:34:27.474Z\"," +
            "      \"job\": {" +
            "        \"id\": \"11111111-1111-1111-1111-111111111111\"," +
            "        \"original_file_name\": \"Test run\"" +
            "      }," +
            "      \"id\": \"11111111-1111-1111-1111-111111111111\"," +
            "      \"content_char_count\": 490," +
            "      \"service\": \"11111111-1111-1111-1111-111111111111\"," +
            "      \"reference\": \"ref\"," +
            "      \"sent_by\": \"mmg\"," +
            "      \"body\": \"this is body\"," +
            "      \"date\": \"2016-07-18T15:34:27.474Z\"" +
            "    }" +
            "  }" +
            "}";


    @Before
    public void before() {
        ConnectorConfiguration configuration = app.getConf();
        NotifyClientProvider notifyClientProvider = new NotifyClientProvider(configuration);
        userNotificationService = new UserNotificationService(notifyClientProvider, configuration);
    }

    @Test
    public void notifyPaymentSuccessEmail() throws Exception {
        notifyEmailMock.responseWithEmailRequestResponse(201, SUCCESS_EMAIL_REQUEST_RESPONSE, -1);
        notifyEmailMock.responseWithEmailCheckStatusResponse(201, SUCCESS_EMAIL_DELIVERY_RESPONSE, -1);

        Optional<String> idOptional = userNotificationService.notifyPaymentSuccessEmail(ChargeEntityFixture.aValidChargeEntity().build());

        String id = idOptional.orElseThrow(() -> new Exception("id not returned from notification service"));
        StatusResponse checkDeliveryStatus = userNotificationService.checkDeliveryStatus(id);
        assertEquals("delivered", checkDeliveryStatus.getStatus());
    }

    @Test
    public void notifyPaymentFailedEmailRequest() throws Exception {
        notifyEmailMock.responseWithEmailRequestResponse(400, BAD_REQUEST_RESPONSE, -1);
        Optional<String> idOptional = userNotificationService.notifyPaymentSuccessEmail(ChargeEntityFixture.aValidChargeEntity().build());
        assertFalse(idOptional.isPresent());
    }

    @Test(expected = GovNotifyClientException.class)
    public void checkDeliveryStatusForNonExistentId() throws Exception {
        notifyEmailMock.responseWithEmailCheckStatusResponse(404, "{}", -1);
        userNotificationService.checkDeliveryStatus("0");
    }
}
