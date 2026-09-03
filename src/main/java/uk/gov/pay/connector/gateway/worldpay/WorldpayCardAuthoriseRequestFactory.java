package uk.gov.pay.connector.gateway.worldpay;

import jakarta.inject.Inject;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.records.WorldpayCardAuthoriseRequest;
import uk.gov.service.payments.commons.model.AuthorisationMode;

import java.util.Optional;

public class WorldpayCardAuthoriseRequestFactory {

    private final WorldpayMotoAuthorisePayloadFactory worldpayMotoAuthorisePayloadFactory;

    @Inject
    public WorldpayCardAuthoriseRequestFactory(WorldpayMotoAuthorisePayloadFactory worldpayMotoAuthorisePayloadFactory) {
        this.worldpayMotoAuthorisePayloadFactory = worldpayMotoAuthorisePayloadFactory;
    }

    public Optional<WorldpayCardAuthoriseRequest> create(CardAuthorisationGatewayRequest request) {
        if (request.isMoto() && !request.isSavePaymentInstrumentToAgreement()
                && request.getAuthorisationMode() == AuthorisationMode.WEB) {
            return Optional.of(worldpayMotoAuthorisePayloadFactory.create(request));
        }

        return Optional.empty();
    }

}
