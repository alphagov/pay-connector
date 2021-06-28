package uk.gov.pay.connector.gatewayaccount.service;

import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.StripeAccountResponse;

import java.util.Optional;

public class StripeAccountService {

    public Optional<StripeAccountResponse> buildStripeAccountResponse(GatewayAccountEntity gatewayAccountEntity) {
        return Optional.ofNullable(gatewayAccountEntity.getCredentials())
                .map(credentials -> credentials.get("stripe_account_id"))
                .map(StripeAccountResponse::new);
    }

}
