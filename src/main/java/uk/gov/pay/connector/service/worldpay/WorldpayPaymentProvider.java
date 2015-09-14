package uk.gov.pay.connector.service.worldpay;


import org.glassfish.jersey.internal.util.Base64;
import uk.gov.pay.connector.model.CardAuthorisationRequest;
import uk.gov.pay.connector.model.CardAuthorisationResponse;
import uk.gov.pay.connector.model.GatewayAccount;
import uk.gov.pay.connector.service.PaymentProvider;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static uk.gov.pay.connector.model.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.model.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.worldpay.template.WorldpayRequestGenerator.anOrderSubmitRequest;

public class WorldpayPaymentProvider implements PaymentProvider {

    private static final String WORLDPAY_URL = "https://secure-test.worldpay.com/jsp/merchant/xml/paymentService.jsp";

    private final Client client;

    public WorldpayPaymentProvider() {
        this(ClientBuilder.newClient());
    }

    public WorldpayPaymentProvider(Client client) {
        this.client = client;
    }

    public CardAuthorisationResponse authorise(GatewayAccount account, CardAuthorisationRequest request) {

        String orderSubmitRequest = anOrderSubmitRequest()
                .withMerchantCode(account.getMerchantId())
                .withTransactionId(request.getTransactionId())
                .withDescription(request.getDescription())
                .withAmount(request.getAmount())
                .withSession(request.getSession())
                .withCard(request.getCard())
                .withBrowser(request.getBrowser())
                .build();

        Response response = xmlRequest(account, orderSubmitRequest);
        return response.getStatus() == 200 ? new CardAuthorisationResponse(true, "", AUTHORISATION_SUCCESS) : new CardAuthorisationResponse(false, "OOOOOOUPS", AUTHORISATION_REJECTED);
    }

    public Response xmlRequest(GatewayAccount account, String request) {

        return client.target(WORLDPAY_URL)
                .request(MediaType.APPLICATION_XML)
                .header("Authorization", encode(account.getGatewayPrincipal(), account.getGatewayPassword()))
                .header(CONTENT_TYPE, APPLICATION_XML)
                .post(Entity.xml(request));
    }

    private static String encode(String username, String password) {
        return "Basic " + Base64.encodeAsString(username + ":" + password);
    }
}
