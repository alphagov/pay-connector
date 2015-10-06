package uk.gov.pay.connector.service.worldpay;


import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.gov.pay.connector.model.*;
import uk.gov.pay.connector.model.domain.GatewayAccount;
import uk.gov.pay.connector.service.GatewayClient;
import uk.gov.pay.connector.service.PaymentProvider;

import javax.ws.rs.core.Response;

import static java.lang.String.format;
import static java.util.UUID.randomUUID;
import static javax.ws.rs.core.Response.Status.OK;
import static uk.gov.pay.connector.model.AuthorisationResponse.*;
import static uk.gov.pay.connector.model.CancelResponse.aSuccessfulCancelResponse;
import static uk.gov.pay.connector.model.CancelResponse.errorCancelResponse;
import static uk.gov.pay.connector.model.CaptureResponse.aSuccessfulCaptureResponse;
import static uk.gov.pay.connector.model.GatewayError.baseGatewayError;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.service.OrderCaptureRequestBuilder.aWorldpayOrderCaptureRequest;
import static uk.gov.pay.connector.service.OrderSubmitRequestBuilder.aWorldpayOrderSubmitRequest;
import static uk.gov.pay.connector.service.worldpay.WorldpayOrderCancelRequestBuilder.aWorldpayOrderCancelRequest;
import static uk.gov.pay.connector.service.worldpay.OrderInquiryRequestBuilder.anOrderInquiryRequest;

public class WorldpayPaymentProvider implements PaymentProvider {
    private final Logger logger = LoggerFactory.getLogger(WorldpayPaymentProvider.class);

    private final GatewayClient client;
    private final GatewayAccount gatewayAccount;

    public WorldpayPaymentProvider(GatewayClient client, GatewayAccount gatewayAccount) {
        this.client = client;
        this.gatewayAccount = gatewayAccount;
    }

    @Override
    public AuthorisationResponse authorise(AuthorisationRequest request) {

        String gatewayTransactionId = generateTransactionId();

        Response response = client.postXMLRequestFor(gatewayAccount, buildOrderSubmitFor(request, gatewayTransactionId));
        return response.getStatus() == OK.getStatusCode() ?
                mapToCardAuthorisationResponse(response, gatewayTransactionId) :
                errorResponse(logger, response);
    }

    @Override
    public CaptureResponse capture(CaptureRequest request) {
        Response response = client.postXMLRequestFor(gatewayAccount, buildOrderCaptureFor(request));
        return response.getStatus() == OK.getStatusCode() ?
                mapToCaptureResponse(response) :
                handleCaptureError(response);
    }

    @Override
    public StatusResponse enquire(ChargeStatusRequest request) {

        Response response = client.postXMLRequestFor(gatewayAccount, buildOrderEnquiryFor(request));
        return mapToStatusResponse(response);
    }

    @Override
    public CancelResponse cancel(CancelRequest request) {
        Response response = client.postXMLRequestFor(gatewayAccount, buildCancelOrderFor(request));
        return response.getStatus() == OK.getStatusCode() ?
                mapToCancelResponse(response) :
                errorCancelResponse(logger, response);
    }

    private String buildOrderCaptureFor(CaptureRequest request) {
        return aWorldpayOrderCaptureRequest()
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

    private String buildCancelOrderFor(CancelRequest request) {
        return aWorldpayOrderCancelRequest()
                .withMerchantCode(gatewayAccount.getUsername())
                .withTransactionId(request.getTransactionId())
                .build();
    }

    private String buildOrderEnquiryFor(ChargeStatusRequest request) {
        return anOrderInquiryRequest()
                .withMerchantCode(gatewayAccount.getUsername()) //TODO: map to the merchant code, not the username!
                .withTransactionId(request.getTransactionId())
                .build();
    }

    private StatusResponse mapToStatusResponse(Response response) {
        WorldpayOrderStatusResponse wResponse = client.unmarshallResponse(response, WorldpayOrderStatusResponse.class);
        StatusResponse statusResponse = new StatusResponse(wResponse.getTransactionId(), wResponse.getLastEvent());
        return statusResponse;
    }

    private AuthorisationResponse mapToCardAuthorisationResponse(Response response, String gatewayTransactionId) {
        WorldpayOrderStatusResponse wResponse = client.unmarshallResponse(response, WorldpayOrderStatusResponse.class);
        if (wResponse.isError()) {
            return authorisationFailureNotUpdateResponse(logger, gatewayTransactionId, wResponse.getErrorMessage());
        }
        return wResponse.isAuthorised() ?
                successfulAuthorisation(AUTHORISATION_SUCCESS, gatewayTransactionId) :
                authorisationFailureResponse(logger, gatewayTransactionId, "Unauthorised");
    }

    private CaptureResponse mapToCaptureResponse(Response response) {
        WorldpayCaptureResponse wResponse = client.unmarshallResponse(response, WorldpayCaptureResponse.class);
        return wResponse.isCaptured() ? aSuccessfulCaptureResponse() : new CaptureResponse(false, baseGatewayError(wResponse.getErrorMessage()));
    }

    private CancelResponse mapToCancelResponse(Response response) {
        WorldpayCancelResponse wResponse = client.unmarshallResponse(response, WorldpayCancelResponse.class);
        return wResponse.isCancelled() ? aSuccessfulCancelResponse() : new CancelResponse(false, baseGatewayError(wResponse.getErrorMessage()));
    }

    private CaptureResponse handleCaptureError(Response response) {
        logger.error(format("Error code received from Worldpay %s.", response.getStatus()));
        return new CaptureResponse(false, baseGatewayError("Error processing capture request"));
    }

    private String generateTransactionId() {
        return randomUUID().toString();
    }

}
