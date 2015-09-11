package uk.gov.pay.connector.worldpay;


import org.glassfish.jersey.internal.util.Base64;
import uk.gov.pay.connector.model.CardAuthorizationRequest;
import uk.gov.pay.connector.model.GatewayAccount;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static uk.gov.pay.connector.worldpay.template.WorldpayRequestGenerator.anOrderSubmitRequest;

public class WorldpayConnector {

    private static final String WORLDPAY_URL = "https://secure-test.worldpay.com/jsp/merchant/xml/paymentService.jsp";

    private final Client client;

    public WorldpayConnector(Client client) {
        this.client = client;
    }

    public Response authorize(GatewayAccount account, CardAuthorizationRequest request) {

        String orderSubmitRequest = anOrderSubmitRequest()
                .withMerchantCode(account.getMerchantId())
                .withTransactionId(request.getTransactionId())
                .withDescription(request.getDescription())
                .withAmount(request.getAmount())
                .withSession(request.getSession())
                .withCard(request.getCard())
                .withBrowser(request.getBrowser())
                .build();
        return xmlRequest(account, orderSubmitRequest);
    }

    public Response xmlRequest(GatewayAccount account, String request) {
        Invocation.Builder defaultRequest = client.target(WORLDPAY_URL).request(MediaType.APPLICATION_XML);

        return defaultRequest
                .header("Authorization", encode(account.getGatewayPrincipal(), account.getGatewayPassword()))
                .header(CONTENT_TYPE, APPLICATION_XML)
                .post(Entity.xml(request));
    }

    private static String encode(String username, String password) {
        return "Basic " + Base64.encodeAsString(username + ":" + password);
    }
}
