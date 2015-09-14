package uk.gov.pay.connector.service;


import uk.gov.pay.connector.service.sandbox.SandboxPaymentProvider;
import uk.gov.pay.connector.service.worldpay.WorldpayPaymentProvider;

public class GatewayResolverFactory {
    public static PaymentProvider resolve(String paymentProviderName) {
        return "worldpay".equals(paymentProviderName) ? new WorldpayPaymentProvider() : new SandboxPaymentProvider();
    }
}
