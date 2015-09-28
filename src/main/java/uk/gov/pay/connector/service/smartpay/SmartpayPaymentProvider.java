package uk.gov.pay.connector.service.smartpay;

import org.glassfish.jersey.internal.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.model.AuthorisationRequest;
import uk.gov.pay.connector.model.AuthorisationResponse;
import uk.gov.pay.connector.model.CaptureRequest;
import uk.gov.pay.connector.model.CaptureResponse;
import uk.gov.pay.connector.model.domain.GatewayAccount;
import uk.gov.pay.connector.service.PaymentProvider;
import uk.gov.pay.connector.util.XMLUnmarshaller;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import javax.xml.bind.JAXBException;

import static java.lang.String.format;
import static java.util.UUID.randomUUID;
import static javax.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static javax.ws.rs.core.Response.Status.OK;
import static uk.gov.pay.connector.model.AuthorisationResponse.*;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.service.OrderSubmitRequestBuilder.aSmartpayOrderSubmitRequest;

public class SmartpayPaymentProvider implements PaymentProvider {

    private static final String MERCHANT_CODE = "MerchantAccount";
    private final Logger logger = LoggerFactory.getLogger(SmartpayPaymentProvider.class);

    private final Client client;
    private final GatewayAccount gatewayAccount;
    private final String gatewayUrl;

    public SmartpayPaymentProvider(Client client, GatewayAccount gatewayAccount, String gatewayUrl) {
        this.client = client;
        this.gatewayAccount = gatewayAccount;
        this.gatewayUrl = gatewayUrl;
    }

    @Override
    public AuthorisationResponse authorise(AuthorisationRequest request) {
        String requestString = buildOrderSubmitFor(request);

        Response response = postRequestFor(gatewayAccount, requestString);

        return response.getStatus() == OK.getStatusCode() ?
                mapToCardAuthorisationResponse(response) :
                errorResponse(logger, response);
    }

    private String buildOrderSubmitFor(AuthorisationRequest request) {

        return aSmartpayOrderSubmitRequest()
                .withMerchantCode(MERCHANT_CODE)
                .withTransactionId(generateTransactionId())
                .withDescription(request.getDescription())
                .withAmount(request.getAmount())
                .withCard(request.getCard())
                .build();
    }

    private AuthorisationResponse mapToCardAuthorisationResponse(Response response) {
        String payload = response.readEntity(String.class);
        try {
            SmartpayAuthorisationResponse sResponse = XMLUnmarshaller.unmarshall(payload, SmartpayAuthorisationResponse.class);
            return sResponse.isAuthorised() ? successfulAuthorisation(AUTHORISATION_SUCCESS, sResponse.getPspReference()) : authorisationFailureResponse(logger, sResponse.getPspReference());
        } catch (JAXBException e) {
            throw unmarshallException(payload, e);
        }
    }

    private RuntimeException unmarshallException(String payload, JAXBException e) {
        String error = format("Could not unmarshall worldpay response %s.", payload);
        logger.error(error, e);
        return new RuntimeException(error, e);
    }

    @Override
    public CaptureResponse capture(CaptureRequest request) {
        return null;
    }

    private Response postRequestFor(GatewayAccount account, String request) {
        return client.target(gatewayUrl)
                .request(APPLICATION_XML)
                .header(AUTHORIZATION, encode(account.getUsername(), account.getPassword()))
                .post(Entity.xml(request));
    }

    private static String encode(String username, String password) {
        return "Basic " + Base64.encodeAsString(username + ":" + password);
    }

    private String generateTransactionId() {
        return randomUUID().toString();
    }
}
