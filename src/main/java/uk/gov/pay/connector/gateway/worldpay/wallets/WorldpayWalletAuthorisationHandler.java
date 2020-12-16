package uk.gov.pay.connector.gateway.worldpay.wallets;

import com.google.inject.name.Named;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.worldpay.WorldpayAuthoriseOrderSessionId;
import uk.gov.pay.connector.gateway.worldpay.WorldpayGatewayResponseGenerator;
import uk.gov.pay.connector.wallets.WalletAuthorisationGatewayRequest;
import uk.gov.pay.connector.wallets.WalletAuthorisationHandler;

import javax.inject.Inject;
import java.net.URI;
import java.util.Map;

import static uk.gov.pay.connector.gateway.util.AuthUtil.getGatewayAccountCredentialsAsAuthHeader;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayAuthoriseWalletOrderRequestBuilder;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;

public class WorldpayWalletAuthorisationHandler implements WalletAuthorisationHandler, WorldpayGatewayResponseGenerator {

    private final GatewayClient authoriseClient;
    private final Map<String, URI> gatewayUrlMap;

    @Inject
    public WorldpayWalletAuthorisationHandler(@Named("WorldpayAuthoriseGatewayClient") GatewayClient authoriseClient,
                                              @Named("WorldpayGatewayUrlMap") Map<String, URI> gatewayUrlMap) {
        this.authoriseClient = authoriseClient;
        this.gatewayUrlMap = gatewayUrlMap;
    }

    @Override
    public GatewayResponse<BaseAuthoriseResponse> authorise(WalletAuthorisationGatewayRequest request) throws GatewayException {
        
        GatewayClient.Response response = authoriseClient.postRequestFor(
                gatewayUrlMap.get(request.getGatewayAccount().getType()), 
                request.getGatewayAccount(), 
                buildWalletAuthoriseOrder(request),
                getGatewayAccountCredentialsAsAuthHeader(request.getGatewayAccount().getCredentials()));
        
        return getWorldpayGatewayResponse(response);
    }

    private GatewayOrder buildWalletAuthoriseOrder(WalletAuthorisationGatewayRequest request) {

        boolean is3dsRequired = request.getGatewayAccount().isRequires3ds();
        boolean isSendIpAddress = request.getGatewayAccount().isSendPayerIpAddressToGateway();

        var builder = aWorldpayAuthoriseWalletOrderRequestBuilder(request.getWalletAuthorisationData().getWalletType());

        if (is3dsRequired && isSendIpAddress) {
            builder.withPayerIpAddress(request.getWalletAuthorisationData().getPaymentInfo().getIpAddress());
        }

        return builder
                .withWalletTemplateData(request.getWalletAuthorisationData())
                .with3dsRequired(is3dsRequired)
                .withSessionId(WorldpayAuthoriseOrderSessionId.of(request.getChargeExternalId()))
                .withUserAgentHeader(request.getWalletAuthorisationData().getPaymentInfo().getUserAgentHeader())
                .withUserAgentHeader(request.getWalletAuthorisationData().getPaymentInfo().getAcceptHeader())
                .withTransactionId(request.getTransactionId().orElse(""))
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withDescription(request.getDescription())
                .withAmount(request.getAmount())
                .build();
    }
}
