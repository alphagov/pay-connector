package uk.gov.pay.connector.gateway.model.request.records;

import jakarta.inject.Inject;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;

import java.util.Optional;

public class CardAuthoriseRequestFactory {

    private final WorldpayCardAuthoriseRequestFactory worldpayCardAuthoriseRequestFactory;

    @Inject
    public CardAuthoriseRequestFactory(WorldpayCardAuthoriseRequestFactory worldpayCardAuthoriseRequestFactory) {
        this.worldpayCardAuthoriseRequestFactory = worldpayCardAuthoriseRequestFactory;
    }

    public Optional<? extends CardAuthoriseRequest> create(CardAuthorisationGatewayRequest request) {
        if (PaymentGatewayName.WORLDPAY.getName().equals(request.getGatewayAccount().getGatewayName())) {
            return worldpayCardAuthoriseRequestFactory.create(request);
        }

        return Optional.empty();
    }

}
