package uk.gov.pay.connector.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.sandbox.SandboxPaymentProvider;
import uk.gov.pay.connector.gateway.stripe.StripePaymentProvider;
import uk.gov.pay.connector.gateway.worldpay.WorldpayPaymentProvider;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;

@ExtendWith(MockitoExtension.class)
class PaymentProvidersTest {

    @Mock
    private WorldpayPaymentProvider worldpayPaymentProvider;
    
    @Mock
    private SandboxPaymentProvider sandboxPaymentProvider;
    
    @Mock
    private StripePaymentProvider stripePaymentProvider;

    private PaymentProviders providers;

    @BeforeEach
    void setup() {
        providers = new PaymentProviders(worldpayPaymentProvider, sandboxPaymentProvider, stripePaymentProvider);
    }

    @Test
    void shouldResolveSandboxPaymentProvider() {
        PaymentProvider sandbox = providers.byName(PaymentGatewayName.SANDBOX);
        assertThat(sandbox, is(instanceOf(SandboxPaymentProvider.class)));
    }

    @Test
    void shouldResolveWorldpayPaymentProvider() {
        PaymentProvider worldpay = providers.byName(PaymentGatewayName.WORLDPAY);
        assertThat(worldpay, is(instanceOf(WorldpayPaymentProvider.class)));
    }
}
