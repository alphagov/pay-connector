package uk.gov.pay.connector.gateway.worldpay.applepay;

import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayErrorException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.worldpay.WorldpayGatewayResponseGenerator;
import uk.gov.pay.connector.wallets.WalletAuthorisationGatewayRequest;
import uk.gov.pay.connector.wallets.WalletAuthorisationHandler;

import java.net.URI;
import java.util.Map;

import static uk.gov.pay.connector.gateway.util.AuthUtil.getGatewayAccountCredentialsAsAuthHeader;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayAuthoriseWalletOrderRequestBuilder;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;

public class WorldpayWalletAuthorisationHandler implements WalletAuthorisationHandler, WorldpayGatewayResponseGenerator {

    private final GatewayClient authoriseClient;
    private final Map<String, URI> gatewayUrlMap;

    public WorldpayWalletAuthorisationHandler(GatewayClient authoriseClient, Map<String, URI> gatewayUrlMap) {
        this.authoriseClient = authoriseClient;
        this.gatewayUrlMap = gatewayUrlMap;
    }

    @Override
    public GatewayResponse<BaseAuthoriseResponse> authorise(WalletAuthorisationGatewayRequest request) throws GatewayErrorException {
        
        GatewayClient.Response response = authoriseClient.postRequestFor(
                gatewayUrlMap.get(request.getGatewayAccount().getType()), 
                request.getGatewayAccount(), 
                buildWalletAuthoriseOrder(request),
                getGatewayAccountCredentialsAsAuthHeader(request.getGatewayAccount()));
        
        return getWorldpayGatewayResponse(response);
    }

    private GatewayOrder buildWalletAuthoriseOrder(WalletAuthorisationGatewayRequest request) {
        return aWorldpayAuthoriseWalletOrderRequestBuilder(request.getWalletAuthorisationData().getWalletType())
                .withWalletTemplateData(request.getWalletAuthorisationData())
                .withSessionId(request.getChargeExternalId())
                .withTransactionId(request.getTransactionId().orElse(""))
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withDescription(request.getDescription())
                .withAmount(request.getAmount())
                .build();
    }
}
