package uk.gov.pay.connector.gateway.model.request.records;

import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;

import java.util.Optional;

public class AuthoriseRequestFactory {

    private final WorldpayAuthoriseRequestFactory worldpayAuthoriseRequestFactory;

    public AuthoriseRequestFactory(WorldpayAuthoriseRequestFactory worldpayAuthoriseRequestFactory) {
        this.worldpayAuthoriseRequestFactory = worldpayAuthoriseRequestFactory;
    }

    public Optional<? extends AuthoriseRequest> create(CardAuthorisationGatewayRequest request) {
        if (PaymentGatewayName.WORLDPAY.toString().equals(request.getGatewayAccount().getGatewayName())) {
            return worldpayAuthoriseRequestFactory.create(request);
        }

        return Optional.empty();
    }

}
