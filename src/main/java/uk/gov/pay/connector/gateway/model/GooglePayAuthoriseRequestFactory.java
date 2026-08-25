package uk.gov.pay.connector.gateway.model;

import jakarta.inject.Inject;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.adyen.AdyenGooglePayAuthorisePayloadFactory;
import uk.gov.pay.connector.gateway.model.request.records.AdyenGooglePayAuthorisePayload;
import uk.gov.pay.connector.wallets.googlepay.GooglePayAuthorisationGatewayRequest;

import java.util.Optional;

public class GooglePayAuthoriseRequestFactory {

    private final AdyenGooglePayAuthorisePayloadFactory adyenGooglePayAuthorisePayloadFactory;

    @Inject
    public GooglePayAuthoriseRequestFactory(AdyenGooglePayAuthorisePayloadFactory adyenGooglePayAuthorisePayloadFactory) {
        this.adyenGooglePayAuthorisePayloadFactory = adyenGooglePayAuthorisePayloadFactory;
    }
    
    public Optional<? extends AdyenGooglePayAuthorisePayload> create(GooglePayAuthorisationGatewayRequest request) {
        if (PaymentGatewayName.ADYEN.getName().equals(request.getGatewayAccount().getGatewayName())) {
            return Optional.of(adyenGooglePayAuthorisePayloadFactory.create(request));
        }

        return Optional.empty();
    }

}
