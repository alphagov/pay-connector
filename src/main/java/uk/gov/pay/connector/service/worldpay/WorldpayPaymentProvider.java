package uk.gov.pay.connector.service.worldpay;


import org.glassfish.jersey.internal.util.Base64;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.model.AuthorisationRequest;
import uk.gov.pay.connector.model.AuthorisationResponse;
import uk.gov.pay.connector.model.CaptureRequest;
import uk.gov.pay.connector.model.CaptureResponse;
import uk.gov.pay.connector.model.GatewayError;
import uk.gov.pay.connector.model.domain.GatewayAccount;
import uk.gov.pay.connector.service.PaymentProvider;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.bind.JAXBException;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static uk.gov.pay.connector.model.AuthorisationResponse.anErrorResponse;
import static uk.gov.pay.connector.model.AuthorisationResponse.successfulAuthorisation;
import static uk.gov.pay.connector.model.CaptureResponse.aSuccessfulCaptureResponse;
import static uk.gov.pay.connector.model.GatewayErrorType.BaseGatewayError;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.utils.EnvironmentUtils.getWorldpayPassword;
import static uk.gov.pay.connector.utils.EnvironmentUtils.getWorldpayUser;
import static uk.gov.pay.connector.worldpay.template.WorldpayRequestGenerator.anOrderCaptureRequest;
import static uk.gov.pay.connector.worldpay.template.WorldpayRequestGenerator.anOrderSubmitRequest;

public class WorldpayPaymentProvider implements PaymentProvider {
    private final Logger logger = LoggerFactory.getLogger(WorldpayPaymentProvider.class);


    private static final String WORLDPAY_URL = "https://secure-test.worldpay.com/jsp/merchant/xml/paymentService.jsp";

    private final Client client;
    private GatewayAccount account;

    public WorldpayPaymentProvider() {
        this(ClientBuilder.newClient(), new GatewayAccount(getWorldpayUser(), getWorldpayPassword()));
    }

    public WorldpayPaymentProvider(Client client, GatewayAccount account) {
        this.client = client;
        this.account = account;
    }

    @Override
    public AuthorisationResponse authorise(AuthorisationRequest request) {

        String orderSubmitRequest = anOrderSubmitRequest()
                .withMerchantCode(account.getMerchantId())
                .withTransactionId(request.getTransactionId())
                .withDescription(request.getDescription())
                .withAmount(request.getAmount())
                .withCard(request.getCard())
                .build();

        Response response = xmlRequest(account, orderSubmitRequest);
        if (response.getStatus() != 200) {
            logger.error(format("Error code received from Worldpay %s.", response.getStatus()));
            return anErrorResponse(new GatewayError("Error processing authorisation request", BaseGatewayError));
        }

        return mapToCardAuthorisationResponse(response, account.getMerchantId());
    }

    @Override
    public CaptureResponse capture(CaptureRequest request) {

        String orderSubmitRequest = anOrderCaptureRequest()
                .withMerchantCode(account.getMerchantId())
                .withTransactionId(request.getTransactionId())
                .withAmount(request.getAmount())
                .withDate(DateTime.now()) //TODO: global timeZone!
                .build();

        Response response = xmlRequest(account, orderSubmitRequest);
        if (response.getStatus() != 200) {
            logger.error(format("Error code received from Worldpay %s.", response.getStatus()));
            return new CaptureResponse(false, new GatewayError("Error processing capture request", BaseGatewayError));
        }
        return mapToCaptureResponse(response);
    }

    private AuthorisationResponse mapToCardAuthorisationResponse(Response response, String gatewayId) {
        String payload = response.readEntity(String.class);
        try {
            WorldpayAuthorisationResponse wResponse = WorldpayXMLUnmarshaller.unmarshall(payload, WorldpayAuthorisationResponse.class);
            if (wResponse.isError()) {
                return anErrorResponse(new GatewayError(wResponse.getErrorMessage(), BaseGatewayError));
            }
            return wResponse.isAuthorised() ? successfulAuthorisation(AUTHORISATION_SUCCESS) : unauthorisedResponse(gatewayId);
        } catch (JAXBException e) {
            logger.error(format("Could not unmarshall worldpay response %s.", payload), e);
            return anErrorResponse(new GatewayError("Error processing authorisation request", BaseGatewayError));
        }
    }

    private CaptureResponse mapToCaptureResponse(Response response) {
        String payload = response.readEntity(String.class);
        try {
            WorldpayCaptureResponse wResponse = WorldpayXMLUnmarshaller.unmarshall(payload, WorldpayCaptureResponse.class);
            return wResponse.isCaptured() ? aSuccessfulCaptureResponse() : new CaptureResponse(false, new GatewayError(wResponse.getErrorMessage(), BaseGatewayError));
        } catch (JAXBException e) {
            return handleJAXBException(payload, e);
        }
    }

    private CaptureResponse handleJAXBException(String payload, JAXBException e) {
        String error = format("Could not unmarshall worldpay response %s.", payload);
        logger.error(error, e);
        throw new RuntimeException(error, e);
    }

    private AuthorisationResponse unauthorisedResponse(String gatewayId) {
        logger.warn(format("Gateway credentials are invalid for %s.", gatewayId));
        return new AuthorisationResponse(false, new GatewayError("This transaction was declined.", BaseGatewayError), AUTHORISATION_REJECTED);
    }

    private Response xmlRequest(GatewayAccount account, String request) {

        return client.target(WORLDPAY_URL)
                .request(MediaType.APPLICATION_XML)
                .header("Authorization", encode(account.getUsername(), account.getPassword()))
                .header(CONTENT_TYPE, APPLICATION_XML)
                .post(Entity.xml(request));
    }

    private static String encode(String username, String password) {
        return "Basic " + Base64.encodeAsString(username + ":" + password);
    }
}
