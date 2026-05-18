package uk.gov.pay.connector.gateway.adyen;

import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.adyen.model.json.Amount;
import uk.gov.pay.connector.gateway.adyen.model.json.BillingAddress;
import uk.gov.pay.connector.gateway.adyen.model.json.Capture;
import uk.gov.pay.connector.gateway.adyen.model.json.PaymentCancelRequest;
import uk.gov.pay.connector.gateway.adyen.model.json.PaymentMethod;
import uk.gov.pay.connector.gateway.adyen.model.json.PaymentRequest;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
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

    public PaymentRequest createPaymentRequest(CardAuthorisationGatewayRequest request) {
        var authCardDetails = request.getAuthCardDetails();
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

        return new PaymentRequest(
                new Amount("GBP", Long.valueOf(request.getAmount())),
                mappedAddress,
                getMerchantAccountId(configuration.getAdyenGatewayConfig(), request.getGatewayAccount().isLive()),
                paymentMethod,
                request.getGovUkPayPaymentId(),
                configuration.getLinks().getFrontendUrl(),
                "Ecommerce",
                adyenCredentials.storeId(),
                "Web",
                new HashMap<>(Map.of("manualCapture", "true"))
        );
    }

    public PaymentCancelRequest createPaymentCancelRequest(CancelGatewayRequest request) {
        return new PaymentCancelRequest(
                request.getExternalChargeId(),
                getMerchantAccountId(configuration.getAdyenGatewayConfig(), request.getGatewayAccount().isLive())
        );
    }

    public Capture createCapturePayload(CaptureGatewayRequest request) {
        return new Capture(
                new Amount("GBP", request.getAmount()),
                getMerchantAccountId(configuration.getAdyenGatewayConfig(), request.getGatewayAccount().isLive())
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
}
