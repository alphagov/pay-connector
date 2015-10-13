package uk.gov.pay.connector.service.smartpay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.model.*;
import uk.gov.pay.connector.model.domain.GatewayAccount;
import uk.gov.pay.connector.service.GatewayClient;
import uk.gov.pay.connector.service.PaymentProvider;

import javax.ws.rs.core.Response;

import static fj.data.Either.reduce;
import static java.util.UUID.randomUUID;
import static javax.ws.rs.core.Response.Status.OK;
import static uk.gov.pay.connector.model.AuthorisationResponse.*;
import static uk.gov.pay.connector.model.CancelResponse.*;
import static uk.gov.pay.connector.model.CaptureResponse.*;
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

        return reduce(
                client
                        .postXMLRequestFor(gatewayAccount, requestString)
                        .bimap(
                                AuthorisationResponse::authorisationFailureResponse,
                                (response) -> response.getStatus() == OK.getStatusCode() ?
                                        mapToCardAuthorisationResponse(response) :
                                        errorResponse(logger, response)
                        )
        );
    }

    @Override
    public CaptureResponse capture(CaptureRequest request) {
        String captureRequestString = buildOrderCaptureFor(request);

        return reduce(
                client
                        .postXMLRequestFor(gatewayAccount, captureRequestString)
                        .bimap(
                                CaptureResponse::captureFailureResponse,
                                (response) -> response.getStatus() == OK.getStatusCode() ?
                                        mapToCaptureResponse(response) :
                                        errorCaptureResponse(logger, response)
                        )
        );
    }

    @Override
    public CancelResponse cancel(CancelRequest request) {
        return reduce(
                client
                        .postXMLRequestFor(gatewayAccount, buildCancelOrderFor(request))
                        .bimap(
                                CancelResponse::cancelFailureResponse,
                                (response) -> response.getStatus() == OK.getStatusCode() ?
                                        mapToCancelResponse(response) :
                                        errorCancelResponse(logger, response)
                        )
        );
    }

    private AuthorisationResponse mapToCardAuthorisationResponse(Response response) {
        return reduce(
                client.unmarshallResponse(response, SmartpayAuthorisationResponse.class)
                        .bimap(
                                AuthorisationResponse::authorisationFailureResponse,
                                (sResponse) -> sResponse.isAuthorised() ?
                                        successfulAuthorisation(AUTHORISATION_SUCCESS, sResponse.getPspReference()) :
                                        authorisationFailureResponse(logger, sResponse.getPspReference(), sResponse.getErrorMessage())
                        )
        );
    }

    private CaptureResponse mapToCaptureResponse(Response response) {
        return reduce(
                client.unmarshallResponse(response, SmartpayCaptureResponse.class)
                        .bimap(
                                CaptureResponse::captureFailureResponse,
                                (sResponse) -> sResponse.isCaptured() ?
                                        aSuccessfulCaptureResponse() :
                                        captureFailureResponse(logger, sResponse.getErrorMessage(), sResponse.getPspReference())
                        )
        );
    }

    private CancelResponse mapToCancelResponse(Response response) {
        return reduce(
                client.unmarshallResponse(response, SmartpayCancelResponse.class)
                        .bimap(
                                CancelResponse::cancelFailureResponse,
                                (sResponse) -> sResponse.isCancelled() ?
                                        aSuccessfulCancelResponse() :
                                        cancelFailureResponse(logger, sResponse.getErrorMessage())
                        )
        );
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
}
