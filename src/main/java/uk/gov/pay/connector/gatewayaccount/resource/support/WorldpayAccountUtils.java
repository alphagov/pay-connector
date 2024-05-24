package uk.gov.pay.connector.gatewayaccount.resource.support;

import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

public class WorldpayAccountUtils {

    public static boolean isWorldpayGatewayAccount(GatewayAccountEntity gatewayAccountEntity) {
        return PaymentGatewayName.WORLDPAY.getName().equals(gatewayAccountEntity.getGatewayName());
    }

}
