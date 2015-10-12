package uk.gov.pay.connector.service;

import org.glassfish.jersey.apache.connector.ApacheConnectorProvider;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.spi.ConnectorProvider;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.GatewayCredentialsConfig;
import uk.gov.pay.connector.service.sandbox.SandboxPaymentProvider;
import uk.gov.pay.connector.service.smartpay.SmartpayPaymentProvider;
import uk.gov.pay.connector.service.worldpay.WorldpayPaymentProvider;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

import static java.lang.String.format;
import static uk.gov.pay.connector.model.domain.GatewayAccount.gatewayAccountFor;
import static uk.gov.pay.connector.resources.PaymentProviderValidator.*;
import static uk.gov.pay.connector.service.GatewayClient.createGatewayClient;

public class PaymentProviders {
    private final PaymentProvider worldpayProvider;
    private final PaymentProvider smartpayProvider;
    private final PaymentProvider sandboxProvider;

    public PaymentProviders(ConnectorConfiguration config) {
        worldpayProvider = createWorldpayProvider(config.getWorldpayConfig());
        smartpayProvider = createSmartPayProvider(config.getSmartpayConfig());
        sandboxProvider = new SandboxPaymentProvider();
    }

    private PaymentProvider createWorldpayProvider(GatewayCredentialsConfig config) {
        return new WorldpayPaymentProvider(
                createGatewayClient(config.getUrl()),
                gatewayAccountFor(config.getUsername(), config.getPassword()));
    }

    private PaymentProvider createSmartPayProvider(GatewayCredentialsConfig config) {
        return new SmartpayPaymentProvider(
                createGatewayClient(config.getUrl()),
                gatewayAccountFor(config.getUsername(), config.getPassword())
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
