package uk.gov.pay.connector.service.smartpay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.model.AuthorisationRequest;
import uk.gov.pay.connector.model.AuthorisationResponse;
import uk.gov.pay.connector.model.CancelRequest;
import uk.gov.pay.connector.model.CancelResponse;
import uk.gov.pay.connector.model.CaptureRequest;
import uk.gov.pay.connector.model.CaptureResponse;
import uk.gov.pay.connector.model.domain.GatewayAccount;
import uk.gov.pay.connector.service.GatewayClient;
import uk.gov.pay.connector.service.PaymentProvider;

import javax.ws.rs.core.Response;

import static java.util.UUID.randomUUID;
import static javax.ws.rs.core.Response.Status.OK;
import static uk.gov.pay.connector.model.AuthorisationResponse.*;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.service.OrderSubmitRequestBuilder.aSmartpayOrderSubmitRequest;

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
        return null;
    }

    @Override
    public CancelResponse cancel(CancelRequest request) {
        throw new IllegalStateException("not yet implemented");
    }

    private AuthorisationResponse mapToCardAuthorisationResponse(Response response) {
        SmartpayAuthorisationResponse sResponse = client.unmarshallResponse(response, SmartpayAuthorisationResponse.class);

        return sResponse.isAuthorised() ?
                successfulAuthorisation(AUTHORISATION_SUCCESS, sResponse.getPspReference()) :
                authorisationFailureResponse(logger, sResponse.getPspReference(), sResponse.getErrorMessage());
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

    private String generateReference() {
        return randomUUID().toString();
    }
}
