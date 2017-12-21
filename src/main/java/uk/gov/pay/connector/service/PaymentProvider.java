package uk.gov.pay.connector.service;

public interface PaymentProvider {
    PaymentGatewayName getPaymentGatewayName();
}
