package uk.gov.pay.connector.gateway.model.request.records;

import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.service.payments.commons.model.AuthorisationMode;

import java.util.Optional;

public class WorldpayAuthoriseRequestFactory {

    private final WorldpayMotoAuthoriseRequestFactory worldpayMotoAuthoriseRequestFactory;

    public WorldpayAuthoriseRequestFactory(WorldpayMotoAuthoriseRequestFactory worldpayMotoAuthoriseRequestFactory) {
        this.worldpayMotoAuthoriseRequestFactory = worldpayMotoAuthoriseRequestFactory;
    }

    public Optional<WorldpayAuthoriseRequest> create(CardAuthorisationGatewayRequest request) {
        if (request.getAuthorisationMode() == AuthorisationMode.WEB && request.isMoto()) {
            return Optional.of(worldpayMotoAuthoriseRequestFactory.create(request));
        }

        return Optional.empty();
    }
    
}
