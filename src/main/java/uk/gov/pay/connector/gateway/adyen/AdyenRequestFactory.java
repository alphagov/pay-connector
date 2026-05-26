package uk.gov.pay.connector.gateway.adyen;

import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.gateway.adyen.request.json.Amount;
import uk.gov.pay.connector.gateway.adyen.request.json.AuthoriseRequestPayload;
import uk.gov.pay.connector.gateway.adyen.request.json.BillingAddress;
import uk.gov.pay.connector.gateway.adyen.request.json.CancelRequestPayload;
import uk.gov.pay.connector.gateway.adyen.request.json.CaptureRequestPayload;
import uk.gov.pay.connector.gateway.adyen.request.json.PaymentMethod;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.AdyenCredentials;
import uk.gov.pay.connector.gatewayaccount.model.GatewayCredentials;
import uk.gov.pay.connector.northamericaregion.NorthAmericaRegion;
import uk.gov.pay.connector.northamericaregion.NorthAmericanRegionMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static uk.gov.pay.connector.gateway.adyen.utils.AdyenConfigUtil.getMerchantAccountId;
import static uk.gov.service.payments.commons.model.AuthorisationMode.MOTO_API;

public class AdyenRequestFactory {

    private final ConnectorConfiguration configuration;

    public AdyenRequestFactory(ConnectorConfiguration configuration) {
        this.configuration = configuration;
    }

    public AuthoriseRequestPayload createPaymentRequest(CardAuthorisationGatewayRequest request) {
        var authCardDetails = request.getAuthCardDetails();

        var mappedAddress = mapToBillingAddress(request);

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
                new HashMap<>(Map.of("manualCapture", "true"))
        );
    }

    private static String getShopperInteraction(CardAuthorisationGatewayRequest request) {
        return request.isMoto() || request.getAuthorisationMode().equals(MOTO_API) ? "Moto" : "Ecommerce";
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

    private static BillingAddress mapToBillingAddress(CardAuthorisationGatewayRequest request) {
        if (request.isMoto() && !request.getAuthorisationMode().equals(MOTO_API)) {
            return null;
        }

        Optional<BillingAddress> result = request.getAuthCardDetails().getAddress().map(address -> {
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
        });
        
        return result.orElse(null);
    }

    private static AdyenCredentials mapToAdyenCredentials(GatewayCredentials gatewayCredentials) {
        if (!(gatewayCredentials instanceof AdyenCredentials)) {
            throw new IllegalArgumentException("Expected provided GatewayCredentials to be of type AdyenCredentials");
        }
        return (AdyenCredentials) gatewayCredentials;
    }
}
