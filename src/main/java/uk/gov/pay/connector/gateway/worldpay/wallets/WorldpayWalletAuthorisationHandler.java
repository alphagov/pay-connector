package uk.gov.pay.connector.gateway.worldpay.wallets;

import com.google.inject.name.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.util.AuthUtil;
import uk.gov.pay.connector.gateway.worldpay.WorldpayAuthoriseOrderSessionId;
import uk.gov.pay.connector.gateway.worldpay.WorldpayGatewayResponseGenerator;
import uk.gov.pay.connector.wallets.WalletAuthorisationGatewayRequest;
import uk.gov.pay.connector.wallets.WalletAuthorisationHandler;
import uk.gov.pay.connector.wallets.WalletType;
import uk.gov.pay.connector.wallets.applepay.AppleDecryptedPaymentData;
import uk.gov.pay.connector.wallets.applepay.ApplePayDecrypter;
import uk.gov.pay.connector.wallets.applepay.api.ApplePayAuthRequest;
import uk.gov.pay.connector.wallets.googlepay.api.GooglePayAuthRequest;
import uk.gov.pay.connector.wallets.model.WalletAuthorisationData;

import javax.inject.Inject;
import java.net.URI;
import java.util.Map;
import java.util.Optional;

import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gateway.util.AuthUtil.getWorldpayAuthHeader;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayAuthoriseWalletOrderRequestBuilder;


public class WorldpayWalletAuthorisationHandler implements WalletAuthorisationHandler, WorldpayGatewayResponseGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorldpayWalletAuthorisationHandler.class);
    private final GatewayClient authoriseClient;
    private final Map<String, URI> gatewayUrlMap;
    private ApplePayDecrypter applePayDecrypter;

    @Inject
    public WorldpayWalletAuthorisationHandler(@Named("WorldpayAuthoriseGatewayClient") GatewayClient authoriseClient,
                                              @Named("WorldpayGatewayUrlMap") Map<String, URI> gatewayUrlMap,
                                              ApplePayDecrypter applePayDecrypter) {
        this.authoriseClient = authoriseClient;
        this.gatewayUrlMap = gatewayUrlMap;
        this.applePayDecrypter = applePayDecrypter;
    }

    @Override
    public GatewayResponse<BaseAuthoriseResponse> authorise(WalletAuthorisationGatewayRequest request) throws GatewayException {

        GatewayClient.Response response = authoriseClient.postRequestFor(
                gatewayUrlMap.get(request.getGatewayAccount().getType()),
                WORLDPAY,
                request.getGatewayAccount().getType(),
                buildWalletAuthoriseOrder(request),
                getWorldpayAuthHeader(request.getGatewayCredentials(), request.getAuthorisationMode(), request.isForRecurringPayment()));

        return getWorldpayGatewayResponse(response);
    }

    private GatewayOrder buildWalletAuthoriseOrder(WalletAuthorisationGatewayRequest request) {

        boolean is3dsRequired = request.getGatewayAccount().isRequires3ds();
        boolean isSendIpAddress = request.getGatewayAccount().isSendPayerIpAddressToGateway();
        boolean isSendPayerEmailToGateway = request.getGatewayAccount().isSendPayerEmailToGateway();

        var builder = aWorldpayAuthoriseWalletOrderRequestBuilder(request.getWalletAuthorisationRequest().getWalletType());

        WalletAuthorisationData walletAuthorisationData = extractWalletAuthorisationData(request);
        
        if (is3dsRequired && isSendIpAddress) {
            builder.withPayerIpAddress(walletAuthorisationData.getPaymentInfo().getIpAddress());
        }

        if (isSendPayerEmailToGateway) {
            Optional.ofNullable(walletAuthorisationData.getPaymentInfo().getEmail()).ifPresent(builder::withPayerEmail);
        }

        return builder
                .withWalletTemplateData(walletAuthorisationData)
                .with3dsRequired(is3dsRequired)
                .withSessionId(WorldpayAuthoriseOrderSessionId.of(request.getGovUkPayPaymentId()))
                .withUserAgentHeader(walletAuthorisationData.getPaymentInfo().getUserAgentHeader())
                .withUserAgentHeader(walletAuthorisationData.getPaymentInfo().getAcceptHeader())
                .withTransactionId(request.getTransactionId().orElse(""))
                .withMerchantCode(AuthUtil.getWorldpayMerchantCode(request.getGatewayCredentials(), request.getAuthorisationMode(), request.isForRecurringPayment()))
                .withDescription(request.getDescription())
                .withAmount(request.getAmount())
                .build();
    }

    private WalletAuthorisationData extractWalletAuthorisationData(WalletAuthorisationGatewayRequest request) {
        if (request.getWalletAuthorisationRequest().getWalletType() == WalletType.APPLE_PAY) {
            return decryptApplePaymentData(request.getGovUkPayPaymentId(), (ApplePayAuthRequest) request.getWalletAuthorisationRequest());
        }
        return (GooglePayAuthRequest) request.getWalletAuthorisationRequest();
    }
    private WalletAuthorisationData decryptApplePaymentData(String chargeId, ApplePayAuthRequest applePayAuthRequest) {
        LOGGER.info("Decrypting apple pay payload for charge with id {}", chargeId);
        AppleDecryptedPaymentData result = applePayDecrypter.performDecryptOperation(applePayAuthRequest);
        result.setPaymentInfo(applePayAuthRequest.getPaymentInfo());
        LOGGER.info("Finished decryption for id {}", chargeId);
        return result;
    }
    
    
}
