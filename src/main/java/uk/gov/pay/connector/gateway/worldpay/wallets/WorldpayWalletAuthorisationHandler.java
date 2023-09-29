package uk.gov.pay.connector.gateway.worldpay.wallets;

import com.google.inject.name.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.request.AuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.util.AuthUtil;
import uk.gov.pay.connector.gateway.worldpay.WorldpayAuthoriseOrderSessionId;
import uk.gov.pay.connector.gateway.worldpay.WorldpayGatewayResponseGenerator;
import uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder;
import uk.gov.pay.connector.wallets.applepay.ApplePayAuthorisationGatewayRequest;
import uk.gov.pay.connector.wallets.googlepay.GooglePayAuthorisationGatewayRequest;
import uk.gov.pay.connector.wallets.applepay.AppleDecryptedPaymentData;
import uk.gov.pay.connector.wallets.applepay.ApplePayDecrypter;
import uk.gov.pay.connector.wallets.applepay.api.ApplePayAuthRequest;
import uk.gov.pay.connector.wallets.googlepay.api.GooglePayAuthRequest;
import uk.gov.pay.connector.wallets.googlepay.api.WorldpayGooglePayAuthRequest;
import uk.gov.pay.connector.wallets.model.WalletPaymentInfo;

import javax.inject.Inject;
import java.net.URI;
import java.util.Map;
import java.util.Optional;

import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gateway.util.AuthUtil.getWorldpayAuthHeader;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayAuthoriseApplePayOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayAuthoriseGooglePayOrderRequestBuilder;


public class WorldpayWalletAuthorisationHandler implements WorldpayGatewayResponseGenerator {

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
    
    public GatewayResponse<BaseAuthoriseResponse> authoriseApplePay(ApplePayAuthorisationGatewayRequest authorisationGatewayRequest) throws GatewayException {
        AppleDecryptedPaymentData appleDecryptedPaymentData = decryptApplePaymentData(authorisationGatewayRequest.getGovUkPayPaymentId(), authorisationGatewayRequest.getApplePayAuthRequest());

        WorldpayOrderRequestBuilder worldpayOrderRequestBuilder = aWorldpayAuthoriseApplePayOrderRequestBuilder();
        worldpayOrderRequestBuilder.withAppleDecryptedPaymentData(appleDecryptedPaymentData);
        
        GatewayOrder gatewayOrder = buildWalletAuthoriseOrder(authorisationGatewayRequest, 
                appleDecryptedPaymentData.getPaymentInfo(), worldpayOrderRequestBuilder);

        return postGatewayRequest(gatewayOrder, authorisationGatewayRequest);
    }

    public GatewayResponse<BaseAuthoriseResponse> authoriseGooglePay(GooglePayAuthorisationGatewayRequest authorisationGatewayRequest) throws GatewayException {
        GooglePayAuthRequest googlePayAuthRequest = authorisationGatewayRequest.getGooglePayAuthRequest();
        WorldpayOrderRequestBuilder worldpayOrderRequestBuilder = aWorldpayAuthoriseGooglePayOrderRequestBuilder();
        worldpayOrderRequestBuilder.withGooglePayPaymentData((WorldpayGooglePayAuthRequest) authorisationGatewayRequest.getGooglePayAuthRequest());

        boolean is3dsRequired = authorisationGatewayRequest.getGatewayAccount().isRequires3ds();
        boolean isSendIpAddress = authorisationGatewayRequest.getGatewayAccount().isSendPayerIpAddressToGateway();
        worldpayOrderRequestBuilder
                .withUserAgentHeader(googlePayAuthRequest.getPaymentInfo().getUserAgentHeader())
                .withAcceptHeader(googlePayAuthRequest.getPaymentInfo().getAcceptHeader())
        .with3dsRequired(is3dsRequired);
        
        if (is3dsRequired && isSendIpAddress) {
            worldpayOrderRequestBuilder.withPayerIpAddress(googlePayAuthRequest.getPaymentInfo().getIpAddress());
        }
        
        GatewayOrder gatewayOrder = buildWalletAuthoriseOrder(authorisationGatewayRequest, 
                googlePayAuthRequest.getPaymentInfo(), worldpayOrderRequestBuilder);

        return postGatewayRequest(gatewayOrder, authorisationGatewayRequest);
    }

    private GatewayResponse postGatewayRequest(GatewayOrder gatewayOrder, AuthorisationGatewayRequest request) throws GatewayException.GenericGatewayException, GatewayException.GatewayErrorException, GatewayException.GatewayConnectionTimeoutException {
        GatewayClient.Response response = authoriseClient.postRequestFor(
                gatewayUrlMap.get(request.getGatewayAccount().getType()),
                WORLDPAY,
                request.getGatewayAccount().getType(),
                gatewayOrder,
                getWorldpayAuthHeader(request.getGatewayCredentials(), request.getAuthorisationMode(), request.isForRecurringPayment()));

        return getWorldpayGatewayResponse(response);
    }

    private GatewayOrder buildWalletAuthoriseOrder(AuthorisationGatewayRequest request, WalletPaymentInfo walletPaymentInfo, WorldpayOrderRequestBuilder builder) {
        boolean isSendPayerEmailToGateway = request.getGatewayAccount().isSendPayerEmailToGateway();

        if (isSendPayerEmailToGateway) {
            Optional.ofNullable(walletPaymentInfo.getEmail()).ifPresent(builder::withPayerEmail);
        }

        return builder
                .withSessionId(WorldpayAuthoriseOrderSessionId.of(request.getGovUkPayPaymentId()))
                .withTransactionId(request.getTransactionId().orElse(""))
                .withMerchantCode(AuthUtil.getWorldpayMerchantCode(request.getGatewayCredentials(), request.getAuthorisationMode(), request.isForRecurringPayment()))
                .withDescription(request.getDescription())
                .withAmount(request.getAmount())
                .build();
    }
    
    private AppleDecryptedPaymentData decryptApplePaymentData(String chargeId, ApplePayAuthRequest applePayAuthRequest) {
        LOGGER.info("Decrypting Apple Pay payload for charge with id {}", chargeId);
        AppleDecryptedPaymentData result = applePayDecrypter.performDecryptOperation(applePayAuthRequest);
        result.setPaymentInfo(applePayAuthRequest.getPaymentInfo());
        return result;
    }
    
    
}
