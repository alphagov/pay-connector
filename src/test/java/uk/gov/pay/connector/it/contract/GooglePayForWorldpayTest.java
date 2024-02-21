package uk.gov.pay.connector.it.contract;

import io.dropwizard.core.setup.Environment;
import org.apache.commons.lang.math.RandomUtils;
import org.apache.http.HttpStatus;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.util.XMLUnmarshaller;
import uk.gov.pay.connector.gateway.worldpay.WorldpayOrderStatusResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.gatewayaccount.model.WorldpayCredentials;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.service.payments.commons.model.AuthorisationMode;

import javax.ws.rs.client.ClientBuilder;
import java.net.URI;
import java.util.List;
import java.util.Map;

import static io.dropwizard.testing.ConfigOverride.config;
import static javax.ws.rs.core.MediaType.APPLICATION_XML_TYPE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gateway.util.AuthUtil.getWorldpayAuthHeader;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

@Ignore
public class GooglePayForWorldpayTest {

    //set these manually
    private String merchantCode = "";
    private String worldpayUsername = "";
    private String worldpayPassword = "";

    //might have to get these manually as they have a 2 week expiry
    private String signature = "MEYCIQDjmS9Y7zlOoLUuL2EwXbMsJdLG/D2II7oDgQx9fLI8zwIhAIt1kHtDJFcyAxaSVKNwXYVM2/6tOG+Pds+Gcefdyb7i";
    private String protocolVersion = "ECv1";
    private String signedMessage = "{\"encryptedMessage\":\"3QcR7WccaLDaSSBn1b4yNY3EjVq16bwxAPF5TITtznbuQCYZCEVWvqQTsJiZA8rChdL94w3CxJYWVrJ580IJvRZ/xsKxa3DN8WnLf9gu6vQ8ky8Xes08qA2wvUNkiJBUtDWE7NmKPxkrmvz2a8KGkAFrrsYghVIBYBXALxadQg9NawHDxZmBrVhxC+NvNtH1GFo0B2mUvyayzxKwqYs8qd7PBiVWEoNFShilLOA65jYhR6C6vEkt+n9BRywtwrBA0f8CX8Ay4TebufWVA6lDAGZWHxWJ2alBrxKE52dhkf6vBxWwDk3GrjAQf97bZ6pfw+hQMZ4n4QLFzvTQD2e1RKNjsaNt5cn2OgvwKM4UbwiByowRMSN9ZPynDG/rsnCkNLBS5sZEMkW3K3LMR1xYPMiBUDnoqEt9ONNYMxUJE+toUF7H6fgBsT/Ju+52kpiDQgwhbwbUw97TKA\\u003d\\u003d\",\"ephemeralPublicKey\":\"BGgZEJ5TvU0BuA5/BYql24H5Ce9N+R7VgoivFv5lH6FyWXvqDQMBlVb4vZGSIR6Ziknpkalk2hYOiSrIkhxUmaM\\u003d\",\"tag\":\"HDfoODJBoosAef3H6NYH9jrUvm2/bmqNZDPTBfOFv4U\\u003d\"}";

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule(
            config("worldpay.urls.test", "https://secure-test.worldpay.com/jsp/merchant/xml/paymentService.jsp"));

    @Test
    public void sendPaymentToWorldpay() throws Exception {
        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity();
        gatewayAccount.setType(GatewayAccountType.TEST);
        gatewayAccount.setGatewayAccountCredentials(
                List.of(aGatewayAccountCredentialsEntity()
                        .withCredentials(Map.of(CREDENTIALS_USERNAME, worldpayUsername, CREDENTIALS_PASSWORD, worldpayPassword))
                        .withGatewayAccountEntity(gatewayAccount)
                        .withPaymentProvider(WORLDPAY.getName())
                        .build()));

        String payload = load("templates/worldpay/WorldpayAuthoriseGooglePayOrderTemplate.xml")
                .replace("${amount}", "100")
                .replace("${merchantCode}", merchantCode)
                .replace("${transactionId?xml}", Integer.toString(RandomUtils.nextInt()))
                .replace("${walletAuthorisationData.encryptedPaymentData.signature?xml}", signature)
                .replace("${walletAuthorisationData.encryptedPaymentData.protocolVersion?xml}", protocolVersion)
                .replace("${walletAuthorisationData.encryptedPaymentData.signedMessage?xml}", signedMessage);

        GatewayClient authoriseClient = getGatewayClient();

        GatewayOrder gatewayOrder = new GatewayOrder(OrderRequestType.AUTHORISE, payload, APPLICATION_XML_TYPE);
        WorldpayCredentials credentials = (WorldpayCredentials)gatewayAccount.getGatewayAccountCredentialsEntity(WORLDPAY.getName()).getCredentialsObject();
        GatewayClient.Response response = authoriseClient.postRequestFor(
                URI.create("https://secure-test.worldpay.com/jsp/merchant/xml/paymentService.jsp"),
                WORLDPAY,
                "test",
                gatewayOrder,
                getWorldpayAuthHeader(credentials, AuthorisationMode.WEB, false));
        assertThat(response.getStatus(), is(HttpStatus.SC_OK));
        String entity = response.getEntity();
        System.out.println(entity);
        WorldpayOrderStatusResponse worldpayOrderStatusResponse = XMLUnmarshaller.unmarshall(entity, WorldpayOrderStatusResponse.class);
        assertTrue(worldpayOrderStatusResponse.getLastEvent().isPresent());
        assertThat(worldpayOrderStatusResponse.getLastEvent(), is("AUTHORISED"));
    }

    private GatewayClient getGatewayClient() {
        Environment environment = app.getInstanceFromGuiceContainer(Environment.class);
        return new GatewayClient(ClientBuilder.newClient(), environment.metrics());
    }
}
