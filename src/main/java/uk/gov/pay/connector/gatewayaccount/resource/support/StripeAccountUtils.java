package uk.gov.pay.connector.gatewayaccount.resource.support;

import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

public class StripeAccountUtils {

    public static boolean isStripeGatewayAccount(GatewayAccountEntity gatewayAccountEntity) {
        return PaymentGatewayName.STRIPE.getName().equals(gatewayAccountEntity.getGatewayName());
    }

}
