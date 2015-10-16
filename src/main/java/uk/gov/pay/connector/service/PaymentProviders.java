package uk.gov.pay.connector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.GatewayCredentialsConfig;
import uk.gov.pay.connector.service.sandbox.SandboxPaymentProvider;
import uk.gov.pay.connector.service.smartpay.SmartpayPaymentProvider;
import uk.gov.pay.connector.service.worldpay.WorldpayPaymentProvider;

import static java.lang.String.format;
import static uk.gov.pay.connector.model.domain.GatewayAccount.gatewayAccountFor;
import static uk.gov.pay.connector.resources.PaymentProviderValidator.*;
import static uk.gov.pay.connector.service.GatewayClient.createGatewayClient;

public class PaymentProviders {
    private final PaymentProvider worldpayProvider;
    private final PaymentProvider smartpayProvider;
    private final PaymentProvider sandboxProvider;

    public PaymentProviders(ConnectorConfiguration config, ObjectMapper objectMapper) {
        this.worldpayProvider = createWorldpayProvider(config.getWorldpayConfig());
        this.smartpayProvider = createSmartPayProvider(config.getSmartpayConfig(), objectMapper);
        this.sandboxProvider = new SandboxPaymentProvider();
    }

    private PaymentProvider createWorldpayProvider(GatewayCredentialsConfig config) {
        return new WorldpayPaymentProvider(
                createGatewayClient(config.getUrl()),
                gatewayAccountFor(config.getUsername(), config.getPassword()));
    }

    private PaymentProvider createSmartPayProvider(GatewayCredentialsConfig config, 
                                                   ObjectMapper objectMapper) {
        return new SmartpayPaymentProvider(
                createGatewayClient(config.getUrl()),
                gatewayAccountFor(config.getUsername(), config.getPassword()),
                objectMapper
        );
    }

    public PaymentProvider resolve(String paymentProviderName) {
        switch (paymentProviderName) {
            case WORLDPAY_PROVIDER:
                return worldpayProvider;
            case SMARTPAY_PROVIDER:
                return smartpayProvider;
            case DEFAULT_PROVIDER:
                return sandboxProvider;
            default:
                throw new RuntimeException(format("Unsupported PaymentProvider %s", paymentProviderName));
        }
    }
}
