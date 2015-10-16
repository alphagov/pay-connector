package uk.gov.pay.connector.it.contract;

import com.google.common.io.Resources;
import org.apache.commons.lang3.tuple.Pair;
import org.hamcrest.CoreMatchers;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.app.GatewayCredentialsConfig;
import uk.gov.pay.connector.model.AuthorisationRequest;
import uk.gov.pay.connector.model.AuthorisationResponse;
import uk.gov.pay.connector.model.ChargeStatusRequest;
import uk.gov.pay.connector.model.StatusUpdates;
import uk.gov.pay.connector.model.domain.Card;
import uk.gov.pay.connector.service.worldpay.WorldpayPaymentProvider;

import javax.ws.rs.client.ClientBuilder;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;

import static com.google.common.io.Resources.getResource;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.model.domain.GatewayAccount.gatewayAccountFor;
import static uk.gov.pay.connector.service.GatewayClient.createGatewayClient;
import static uk.gov.pay.connector.util.CardUtils.aValidCard;
import static uk.gov.pay.connector.util.SystemUtils.envOrThrow;

public class WorldpayPaymentProviderTest {

    @Before
    public void checkThatWorldpayIsUp(){
        try {
            new URL(getWorldpayConfig().getUrl()).openConnection().connect();
        } catch(IOException ex) {
            Assume.assumeTrue(false);
        }
    }

    @Test
    public void shouldBeAbleToSendAuthorisationRequestForMerchant() throws Exception {
        GatewayCredentialsConfig config = getWorldpayConfig();
        WorldpayPaymentProvider connector = new WorldpayPaymentProvider(
                createGatewayClient(
                        ClientBuilder.newClient(),
                        config.getUrl()
                ),
                gatewayAccountFor(config.getUsername(), config.getPassword())
        );
        AuthorisationRequest request = getCardAuthorisationRequest();
        AuthorisationResponse response = connector.authorise(request);

        assertTrue(response.isSuccessful());
    }

    @Test
    public void shouldBeAbleToSendOrderInquiryRequestWhenStatusNotificationComesIn() throws Exception {
        GatewayCredentialsConfig config = getWorldpayConfig();
        WorldpayPaymentProvider connector = new WorldpayPaymentProvider(
                createGatewayClient(
                        ClientBuilder.newClient(),
                        config.getUrl()
                ),
                gatewayAccountFor(config.getUsername(), config.getPassword())
        );

        String transactionId = "c15b9283-5205-45e0-8019-883c3319e838";
        StatusUpdates statusResponse = connector.newStatusFromNotification(notificationPayloadForTransaction(transactionId));

        assertThat(statusResponse.getStatusUpdates(), hasItem(Pair.of(transactionId, CAPTURED)));
    }

    @Test
    public void shouldFailRequestAuthorisationIfCredentialsAreNotCorrect() throws Exception {
        String worldpayUrl = getWorldpayConfig().getUrl();

        WorldpayPaymentProvider connector = new WorldpayPaymentProvider(
                createGatewayClient(ClientBuilder.newClient(), worldpayUrl),
                gatewayAccountFor("wrongUsername", "wrongPassword"));

        AuthorisationRequest request = getCardAuthorisationRequest();
        AuthorisationResponse response = connector.authorise(request);

        assertFalse(response.isSuccessful());
    }

    private AuthorisationRequest getCardAuthorisationRequest() {
        Card card = aValidCard();
        String amount = "500";
        String description = "This is the description";
        return new AuthorisationRequest("chargeId", card, amount, description);
    }


    private GatewayCredentialsConfig getWorldpayConfig() {
        return WORLDPAY_CREDENTIALS;
    }

    private static final GatewayCredentialsConfig WORLDPAY_CREDENTIALS = new GatewayCredentialsConfig() {
        @Override
        public String getUrl() {
            return "https://secure-test.worldpay.com/jsp/merchant/xml/paymentService.jsp";
        }

        @Override
        public String getUsername() {
            return "MERCHANTCODE";
        }

        @Override
        public String getPassword() {
            return envOrThrow("GDS_CONNECTOR_WORLDPAY_PASSWORD");
        }
    };

    private String notificationPayloadForTransaction(String transactionId) throws IOException {
        URL resource = getResource("templates/worldpay/notification.xml");
        return Resources.toString(resource, Charset.defaultCharset()).replace("{{transactionId}}", transactionId);
    }

}
