package uk.gov.pay.connector.notification;

import uk.gov.pay.connector.service.PaymentGatewayName;

public interface PaymentProvider {
    PaymentGatewayName getPaymentGatewayName();
}
