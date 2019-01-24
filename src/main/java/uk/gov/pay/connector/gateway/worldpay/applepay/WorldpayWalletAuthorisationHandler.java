package uk.gov.pay.connector.gateway.worldpay.applepay;

import fj.data.Either;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.util.GatewayResponseGenerator;
import uk.gov.pay.connector.gateway.worldpay.WorldpayOrderStatusResponse;
import uk.gov.pay.connector.wallets.WalletAuthorisationHandler;
import uk.gov.pay.connector.wallets.WalletAuthorisationGatewayRequest;

import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayAuthoriseWalletOrderRequestBuilder;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;

public class WorldpayWalletAuthorisationHandler implements WalletAuthorisationHandler {

    private final GatewayClient authoriseClient;

    public WorldpayWalletAuthorisationHandler(GatewayClient authoriseClient) {
        this.authoriseClient = authoriseClient;
    }

    @Override
    public GatewayResponse<BaseAuthoriseResponse> authorise(WalletAuthorisationGatewayRequest request) {
        Either<GatewayError, GatewayClient.Response> response = authoriseClient.postRequestFor(null, request.getGatewayAccount(), buildWalletAuthoriseOrder(request));
        return GatewayResponseGenerator.getWorldpayGatewayResponse(authoriseClient, response, WorldpayOrderStatusResponse.class);
    }

    private GatewayOrder buildWalletAuthoriseOrder(WalletAuthorisationGatewayRequest request) {
        return aWorldpayAuthoriseWalletOrderRequestBuilder(request.getWalletTemplateData().getWalletType())
                .withWalletTemplateData(request.getWalletTemplateData())
                .withSessionId(request.getChargeExternalId())
                .withTransactionId(request.getTransactionId().orElse(""))
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withDescription(request.getDescription())
                .withAmount(request.getAmount())
                .build();
    }
}
