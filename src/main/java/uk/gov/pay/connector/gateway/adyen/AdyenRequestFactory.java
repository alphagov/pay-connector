package uk.gov.pay.connector.gateway.adyen;

import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.adyen.model.json.Amount;
import uk.gov.pay.connector.gateway.adyen.model.json.BillingAddress;
import uk.gov.pay.connector.gateway.adyen.model.json.PaymentMethod;
import uk.gov.pay.connector.gateway.adyen.model.json.PaymentRequest;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.AdyenCredentials;
import uk.gov.pay.connector.gatewayaccount.model.GatewayCredentials;
import uk.gov.pay.connector.northamericaregion.NorthAmericaRegion;
import uk.gov.pay.connector.northamericaregion.NorthAmericanRegionMapper;

import static uk.gov.pay.connector.gateway.adyen.utils.AdyenConfigUtil.getMerchantAccountId;

class AdyenRequestFactory {

    private final ConnectorConfiguration configuration;

    AdyenRequestFactory(ConnectorConfiguration configuration) {
        this.configuration = configuration;
    }

    PaymentRequest createPaymentRequest(CardAuthorisationGatewayRequest request) {
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
                "Web"
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
