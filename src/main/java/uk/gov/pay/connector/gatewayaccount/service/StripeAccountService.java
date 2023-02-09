package uk.gov.pay.connector.gatewayaccount.service;

import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.StripeAccountResponse;

import java.util.Optional;

import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;

public class StripeAccountService {

    public Optional<StripeAccountResponse> buildStripeAccountResponse(GatewayAccountEntity gatewayAccountEntity) {
        return Optional.ofNullable(gatewayAccountEntity.getCredentials(STRIPE.getName()))
                .map(credentials -> credentials.get("stripe_account_id") != null ?
                        credentials.get("stripe_account_id").toString() : null)
                .map(StripeAccountResponse::new);
    }

}
