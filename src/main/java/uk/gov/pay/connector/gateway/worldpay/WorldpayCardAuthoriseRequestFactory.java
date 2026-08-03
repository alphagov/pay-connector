package uk.gov.pay.connector.gateway.worldpay;

import jakarta.inject.Inject;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.records.WorldpayCardAuthoriseRequest;
import uk.gov.service.payments.commons.model.AuthorisationMode;

import java.util.Optional;

public class WorldpayCardAuthoriseRequestFactory {

    private final WorldpayMotoAuthoriseRequestFactory worldpayMotoAuthoriseRequestFactory;

    @Inject
    public WorldpayCardAuthoriseRequestFactory(WorldpayMotoAuthoriseRequestFactory worldpayMotoAuthoriseRequestFactory) {
        this.worldpayMotoAuthoriseRequestFactory = worldpayMotoAuthoriseRequestFactory;
    }

    public Optional<WorldpayCardAuthoriseRequest> create(CardAuthorisationGatewayRequest request) {
        if (request.isMoto() && !request.isSavePaymentInstrumentToAgreement()
                && request.getAuthorisationMode() == AuthorisationMode.WEB) {
            return Optional.of(worldpayMotoAuthoriseRequestFactory.create(request));
        }

        return Optional.empty();
    }

}
