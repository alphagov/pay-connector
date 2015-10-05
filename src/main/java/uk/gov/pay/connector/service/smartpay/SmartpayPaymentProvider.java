package uk.gov.pay.connector.service.smartpay;

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
import static uk.gov.pay.connector.model.CaptureResponse.captureFailureResponse;
import static uk.gov.pay.connector.model.GatewayError.baseGatewayError;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.service.OrderCaptureRequestBuilder.aSmartpayOrderCaptureRequest;
import static uk.gov.pay.connector.service.OrderSubmitRequestBuilder.aSmartpayOrderSubmitRequest;
import static uk.gov.pay.connector.service.smartpay.SmartpayOrderCancelRequestBuilder.aSmartpayOrderCancelRequest;

public class SmartpayPaymentProvider implements PaymentProvider {

    private static final String MERCHANT_CODE = "MerchantAccount";
    private final Logger logger = LoggerFactory.getLogger(SmartpayPaymentProvider.class);

    private final GatewayClient client;
    private final GatewayAccount gatewayAccount;

    public SmartpayPaymentProvider(GatewayClient client, GatewayAccount gatewayAccount) {
        this.client = client;
        this.gatewayAccount = gatewayAccount;
    }

    @Override
    public AuthorisationResponse authorise(AuthorisationRequest request) {
        String requestString = buildOrderSubmitFor(request);

        Response response = client.postXMLRequestFor(gatewayAccount, requestString);

        return response.getStatus() == OK.getStatusCode() ?
                mapToCardAuthorisationResponse(response) :
                errorResponse(logger, response);
    }

    @Override
    public CaptureResponse capture(CaptureRequest request) {
        String captureRequestString = buildOrderCaptureFor(request);
        logger.debug("captureRequestString = " + captureRequestString);
        Response response = client.postXMLRequestFor(gatewayAccount, captureRequestString);
        return response.getStatus() == OK.getStatusCode() ?
                mapToCaptureResponse(response) :
                handleCaptureError(response);
    }

    @Override
    public CancelResponse cancel(CancelRequest request) {
        Response response = client.postXMLRequestFor(gatewayAccount, buildCancelOrderFor(request));
        return response.getStatus() == OK.getStatusCode() ?
                mapToCancelResponse(response) :
                errorCancelResponse(logger, response);
    }

    private AuthorisationResponse mapToCardAuthorisationResponse(Response response) {
        SmartpayAuthorisationResponse sResponse = client.unmarshallResponse(response, SmartpayAuthorisationResponse.class);

        return sResponse.isAuthorised() ?
                successfulAuthorisation(AUTHORISATION_SUCCESS, sResponse.getPspReference()) :
                authorisationFailureResponse(logger, sResponse.getPspReference(), sResponse.getErrorMessage());
    }

    private CancelResponse mapToCancelResponse(Response response) {
        SmartpayCancelResponse spResponse = client.unmarshallResponse(response, SmartpayCancelResponse.class);
        return spResponse.isCancelled() ? aSuccessfulCancelResponse() : new CancelResponse(false, baseGatewayError(spResponse.getErrorMessage()));
    }

    private String buildOrderSubmitFor(AuthorisationRequest request) {
        return aSmartpayOrderSubmitRequest()
                .withMerchantCode(MERCHANT_CODE)
                .withTransactionId(generateReference())
                .withDescription(request.getDescription())
                .withAmount(request.getAmount())
                .withCard(request.getCard())
                .build();
    }

    private String buildCancelOrderFor(CancelRequest request) {
        return aSmartpayOrderCancelRequest()
                .withMerchantCode(MERCHANT_CODE)
                .withTransactionId(request.getTransactionId())
                .build();
    }

    private String buildOrderCaptureFor(CaptureRequest request) {
        return aSmartpayOrderCaptureRequest()
                .withMerchantCode(MERCHANT_CODE)
                .withTransactionId(request.getTransactionId())
                .withAmount(request.getAmount())
                .build();
    }

    private String generateReference() {
        return randomUUID().toString();
    }

    private CaptureResponse mapToCaptureResponse(Response response) {
        SmartpayCaptureResponse sResponse = client.unmarshallResponse(response, SmartpayCaptureResponse.class);
        return sResponse.isCaptured() ?
                aSuccessfulCaptureResponse() :
                captureFailureResponse(logger, sResponse.getErrorMessage(), sResponse.getPspRefrence());
    }

    private CaptureResponse handleCaptureError(Response response) {
        logger.error(format("Error code received from provider: response status = %s.", response.getStatus()));
        return new CaptureResponse(false, baseGatewayError("Error processing capture request"));
    }
}
