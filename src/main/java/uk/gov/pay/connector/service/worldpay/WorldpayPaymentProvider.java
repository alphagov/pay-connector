package uk.gov.pay.connector.service.worldpay;


import org.glassfish.jersey.internal.util.Base64;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.WorldpayConfig;
import uk.gov.pay.connector.model.AuthorisationRequest;
import uk.gov.pay.connector.model.AuthorisationResponse;
import uk.gov.pay.connector.model.CaptureRequest;
import uk.gov.pay.connector.model.CaptureResponse;
import uk.gov.pay.connector.model.domain.GatewayAccount;
import uk.gov.pay.connector.service.PaymentProvider;
import uk.gov.pay.connector.util.XMLUnmarshaller;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import javax.xml.bind.JAXBException;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static java.lang.String.format;
import static java.util.UUID.randomUUID;
import static javax.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static javax.ws.rs.core.Response.Status.OK;
import static uk.gov.pay.connector.model.AuthorisationResponse.*;
import static uk.gov.pay.connector.model.CaptureResponse.aSuccessfulCaptureResponse;
import static uk.gov.pay.connector.model.GatewayError.baseGatewayError;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.model.domain.GatewayAccount.gatewayAccountFor;
import static uk.gov.pay.connector.service.worldpay.WorldpayOrderCaptureRequestBuilder.anOrderCaptureRequest;
import static uk.gov.pay.connector.service.OrderSubmitRequestBuilder.aWorldpayOrderSubmitRequest;

public class WorldpayPaymentProvider implements PaymentProvider {
    private final Logger logger = LoggerFactory.getLogger(WorldpayPaymentProvider.class);

    private final String worldpayUrl;

    private final Client client;
    private final GatewayAccount gatewayAccount;

    public WorldpayPaymentProvider(WorldpayConfig worldpayConfig) {
        this(ClientBuilder.newClient(),
                gatewayAccountFor(worldpayConfig.getUsername(), worldpayConfig.getPassword()),
                worldpayConfig.getUrl());
    }

    public WorldpayPaymentProvider(Client client, GatewayAccount gatewayAccount, String worldpayUrl) {
        this.client = client;
        this.gatewayAccount = gatewayAccount;
        this.worldpayUrl = worldpayUrl;
    }

    @Override
    public AuthorisationResponse authorise(AuthorisationRequest request) {

        String gatewayTransactionId = generateTransactionId();

        Response response = postRequestFor(gatewayAccount, buildOrderSubmitFor(request, gatewayTransactionId));
        return response.getStatus() == OK.getStatusCode() ?
                mapToCardAuthorisationResponse(response, gatewayTransactionId) :
                errorResponse(logger, response);
    }

    @Override
    public CaptureResponse capture(CaptureRequest request) {

        Response response = postRequestFor(gatewayAccount, buildOrderCaptureFor(request));
        return response.getStatus() == OK.getStatusCode() ?
                mapToCaptureResponse(response) :
                handleCaptureError(response);
    }

    private String buildOrderCaptureFor(CaptureRequest request) {
        return anOrderCaptureRequest()
                .withMerchantCode(gatewayAccount.getUsername())
                .withTransactionId(request.getTransactionId())
                .withAmount(request.getAmount())
                .withDate(DateTime.now(DateTimeZone.UTC))
                .build();
    }

    private String buildOrderSubmitFor(AuthorisationRequest request, String gatewayTransactionId) {
        return aWorldpayOrderSubmitRequest()
                .withMerchantCode(gatewayAccount.getUsername())
                .withTransactionId(gatewayTransactionId)
                .withDescription(request.getDescription())
                .withAmount(request.getAmount())
                .withCard(request.getCard())
                .build();
    }

    private AuthorisationResponse mapToCardAuthorisationResponse(Response response, String gatewayTransactionId) {
        String payload = response.readEntity(String.class);
        try {
            WorldpayAuthorisationResponse wResponse = XMLUnmarshaller.unmarshall(payload, WorldpayAuthorisationResponse.class);
            if (wResponse.isError()) {
                return authorisationFailureNotUpdateResponse(logger, gatewayTransactionId, wResponse.getErrorMessage());
            }
            return wResponse.isAuthorised() ? successfulAuthorisation(AUTHORISATION_SUCCESS, gatewayTransactionId) : authorisationFailureResponse(logger, gatewayTransactionId);
        } catch (JAXBException e) {
            throw unmarshallException(payload, e);
        }
    }

    private CaptureResponse mapToCaptureResponse(Response response) {
        String payload = response.readEntity(String.class);
        try {
            WorldpayCaptureResponse wResponse = XMLUnmarshaller.unmarshall(payload, WorldpayCaptureResponse.class);
            return wResponse.isCaptured() ? aSuccessfulCaptureResponse() : new CaptureResponse(false, baseGatewayError(wResponse.getErrorMessage()));
        } catch (JAXBException e) {
            throw unmarshallException(payload, e);
        }
    }

    private Response postRequestFor(GatewayAccount account, String request) {

        return client.target(worldpayUrl)
                .request(APPLICATION_XML)
                .header(AUTHORIZATION, encode(account.getUsername(), account.getPassword()))
                .header(CONTENT_TYPE, APPLICATION_XML)
                .post(Entity.xml(request));
    }

    private RuntimeException unmarshallException(String payload, JAXBException e) {
        String error = format("Could not unmarshall worldpay response %s.", payload);
        logger.error(error, e);
        return new RuntimeException(error, e);
    }

    private CaptureResponse handleCaptureError(Response response) {
        logger.error(format("Error code received from Worldpay %s.", response.getStatus()));
        return new CaptureResponse(false, baseGatewayError("Error processing capture request"));
    }

    private static String encode(String username, String password) {
        return "Basic " + Base64.encodeAsString(username + ":" + password);
    }

    private String generateTransactionId() {
        return randomUUID().toString();
    }

}
