package uk.gov.pay.connector.gateway.model.request.records;

import jakarta.inject.Inject;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.wallets.applepay.ApplePayAuthorisationGatewayRequest;

import java.util.Optional;

public class ApplePayAuthoriseRequestFactory {

    private final AdyenApplePayAuthoriseRequestFactory  adyenApplePayAuthoriseRequestFactory;

    @Inject
    public ApplePayAuthoriseRequestFactory(AdyenApplePayAuthoriseRequestFactory adyenApplePayAuthoriseRequestFactory) {
        this.adyenApplePayAuthoriseRequestFactory = adyenApplePayAuthoriseRequestFactory;
    }
    
    public Optional<? extends ApplePayAuthoriseRequest> create(ApplePayAuthorisationGatewayRequest request) {
        if (PaymentGatewayName.ADYEN.getName().equals(request.getGatewayAccount().getGatewayName())) {
            return Optional.of(adyenApplePayAuthoriseRequestFactory.create(request));
        }

        return Optional.empty();
    }

}
