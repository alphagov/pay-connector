package uk.gov.pay.connector.gatewayaccount.service;

import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.StripeAccountResponse;
import uk.gov.pay.connector.gatewayaccount.model.StripeCredentials;

import java.util.Optional;

import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;

public class StripeAccountService {

    public Optional<StripeAccountResponse> buildStripeAccountResponse(GatewayAccountEntity gatewayAccountEntity) {
        return Optional.ofNullable(gatewayAccountEntity.getGatewayAccountCredentialsEntity(STRIPE.getName()))
                .map(credentials -> ((StripeCredentials)credentials.getCredentialsObject()).getStripeAccountId())
                .map(StripeAccountResponse::new);
    }

}
