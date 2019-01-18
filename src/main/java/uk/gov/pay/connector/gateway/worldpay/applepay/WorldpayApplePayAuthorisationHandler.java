package uk.gov.pay.connector.gateway.worldpay.applepay;

import fj.data.Either;
import uk.gov.pay.connector.wallets.applepay.ApplePayAuthorisationGatewayRequest;
import uk.gov.pay.connector.wallets.applepay.ApplePayAuthorisationHandler;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.util.GatewayResponseGenerator;
import uk.gov.pay.connector.gateway.worldpay.WorldpayOrderStatusResponse;

import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayAuthoriseApplePayOrderRequestBuilder;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;

public class WorldpayApplePayAuthorisationHandler implements ApplePayAuthorisationHandler {

    private final GatewayClient authoriseClient;

    public WorldpayApplePayAuthorisationHandler(GatewayClient authoriseClient) {
        this.authoriseClient = authoriseClient;
    }

    @Override
    public GatewayResponse<BaseAuthoriseResponse> authorise(ApplePayAuthorisationGatewayRequest request) {
        Either<GatewayError, GatewayClient.Response> response = authoriseClient.postRequestFor(null, request.getGatewayAccount(), buildApplePayAuthoriseOrder(request));
        return GatewayResponseGenerator.getWorldpayGatewayResponse(authoriseClient, response, WorldpayOrderStatusResponse.class);
    }

    private GatewayOrder buildApplePayAuthoriseOrder(ApplePayAuthorisationGatewayRequest request) {
        return aWorldpayAuthoriseApplePayOrderRequestBuilder()
                .withApplePayTemplateData(ApplePayTemplateData.from(request.getAppleDecryptedPaymentData()))
                .withSessionId(request.getChargeExternalId())
                .withTransactionId(request.getTransactionId().orElse(""))
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withDescription(request.getDescription())
                .withAmount(request.getAmount())
                .build();
    }
}
