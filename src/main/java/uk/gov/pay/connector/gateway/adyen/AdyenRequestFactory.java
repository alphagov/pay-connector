package uk.gov.pay.connector.gateway.adyen;

import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.adyen.request.json.Amount;
import uk.gov.pay.connector.gateway.adyen.request.json.AuthoriseRequestPayload;
import uk.gov.pay.connector.gateway.adyen.request.json.BillingAddress;
import uk.gov.pay.connector.gateway.adyen.request.json.CancelRequestPayload;
import uk.gov.pay.connector.gateway.adyen.request.json.CaptureRequestPayload;
import uk.gov.pay.connector.gateway.adyen.request.json.PaymentMethod;
import uk.gov.pay.connector.gateway.adyen.response.json.BrowserInfo;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.adyen.request.json.RefundRequestPayload;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.AdyenCredentials;
import uk.gov.pay.connector.gatewayaccount.model.GatewayCredentials;
import uk.gov.pay.connector.northamericaregion.NorthAmericaRegion;
import uk.gov.pay.connector.northamericaregion.NorthAmericanRegionMapper;

import java.util.HashMap;
import java.util.Map;

import static uk.gov.pay.connector.gateway.adyen.utils.AdyenConfigUtil.getMerchantAccountId;

public class AdyenRequestFactory {

    private final ConnectorConfiguration configuration;

    public AdyenRequestFactory(ConnectorConfiguration configuration) {
        this.configuration = configuration;
    }

    public AuthoriseRequestPayload createPaymentRequest(CardAuthorisationGatewayRequest request) {
        var authCardDetails = request.getAuthCardDetails();
        boolean isMoto = "Moto".equals(getShopperInteraction(request));

        var mappedAddress = authCardDetails.getAddress()
                .map(AdyenRequestFactory::mapToBillingAddress)
                .orElse(null);

        var paymentMethod = new PaymentMethod(authCardDetails.getCvc(),
                authCardDetails.getEndDate().getTwoDigitMonth(),
                authCardDetails.getEndDate().getFourDigitYear(),
                authCardDetails.getCardHolder(),
                authCardDetails.getCardNo(),
                "scheme");

        var adyenCredentials = mapToAdyenCredentials(request.getGatewayCredentials());

        return new AuthoriseRequestPayload(
                new Amount("GBP", Long.valueOf(request.getAmount())),
                mappedAddress,
                getMerchantAccountId(configuration.getAdyenGatewayConfig(), request.getGatewayAccount().isLive()),
                paymentMethod,
                request.getGovUkPayPaymentId(),
                configuration.getLinks().getFrontendUrl(),
                getShopperInteraction(request),
                adyenCredentials.storeId(),
                "Web",
                new HashMap<>(Map.of("manualCapture", "true")),
                 isMoto ? null : mapToBrowserInfo(authCardDetails), 
                 isMoto ? null : configuration.getLinks().getFrontendUrl(),
                 isMoto ? null : request.getEmail(),
                 isMoto ? null : authCardDetails.getIpAddress().orElse(null)
        );
    }

    private static String getShopperInteraction(CardAuthorisationGatewayRequest request) {
        return request.isMoto() ? "Moto" : "Ecommerce";
    }

    public CancelRequestPayload createPaymentCancelRequest(CancelGatewayRequest request) {
        return new CancelRequestPayload(
                request.getExternalChargeId(),
                getMerchantAccountId(configuration.getAdyenGatewayConfig(), request.getGatewayAccount().isLive())
        );
    }

    public CaptureRequestPayload createCapturePayload(CaptureGatewayRequest request) {
        return new CaptureRequestPayload(
                new Amount("GBP", request.getAmount()),
                getMerchantAccountId(configuration.getAdyenGatewayConfig(), request.getGatewayAccount().isLive())
        );
    }

    public RefundRequestPayload createRefundRequestPayload(RefundGatewayRequest request) {
        var adyenCredentials = mapToAdyenCredentials(request.getGatewayCredentials());
        return new RefundRequestPayload(
                getMerchantAccountId(configuration.getAdyenGatewayConfig(), request.getGatewayAccount().isLive()),
                new Amount("GBP", Long.valueOf(request.getAmount())),
                request.getRefundExternalId(),
                adyenCredentials.storeId()
        );
    }

    private static BillingAddress mapToBillingAddress(Address address) {
        var northAmericanRegionMapper = new NorthAmericanRegionMapper();
        String stateOrProvince = northAmericanRegionMapper.getNorthAmericanRegionForCountry(address)
                .map(NorthAmericaRegion::getFullName)
                .orElse(null);
        return new BillingAddress(
                address.getLine1(),
                address.getLine2(),
                address.getCity(),
                address.getCountry(),
                address.getPostcode(),
                stateOrProvince);
    }

    private static AdyenCredentials mapToAdyenCredentials(GatewayCredentials gatewayCredentials) {
        if (!(gatewayCredentials instanceof AdyenCredentials)) {
            throw new IllegalArgumentException("Expected provided GatewayCredentials to be of type AdyenCredentials");
        }
        return (AdyenCredentials) gatewayCredentials;
    }

    private BrowserInfo mapToBrowserInfo(AuthCardDetails authCardDetails) {
        return new BrowserInfo(
                authCardDetails.getAcceptHeader(),
                authCardDetails.getJsScreenColorDepth().map(Integer::valueOf).orElse(null),
                authCardDetails.getJsEnabled(),
                authCardDetails.getJsNavigatorLanguage().map(String::valueOf).orElse(null),
                authCardDetails.getJsScreenHeight().map(Integer::valueOf).orElse(null),
                authCardDetails.getJsScreenWidth().map(Integer::valueOf).orElse(null),
                authCardDetails.getJsTimezoneOffsetMins().map(Integer::valueOf).orElse(null),
                authCardDetails.getUserAgentHeader()
        );
    }
}
