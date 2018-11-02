package uk.gov.pay.connector.service;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.epdq.EpdqPaymentProvider;
import uk.gov.pay.connector.gateway.sandbox.SandboxPaymentProvider;
import uk.gov.pay.connector.gateway.smartpay.SmartpayPaymentProvider;
import uk.gov.pay.connector.gateway.stripe.StripePaymentProvider;
import uk.gov.pay.connector.gateway.worldpay.WorldpayPaymentProvider;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.EPDQ;

@RunWith(MockitoJUnitRunner.class)
public class PaymentProvidersTest {

    @Mock
    WorldpayPaymentProvider worldpayPaymentProvider;
    
    @Mock
    EpdqPaymentProvider epdqPaymentProvider;
    
    @Mock
    SmartpayPaymentProvider smartpayPaymentProvider;
    
    @Mock
    SandboxPaymentProvider sandboxPaymentProvider;
    
    @Mock
    StripePaymentProvider stripePaymentProvider;
    
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private PaymentProviders providers;

    @Before
    public void setup() {
        providers = new PaymentProviders(worldpayPaymentProvider, epdqPaymentProvider, smartpayPaymentProvider, sandboxPaymentProvider, stripePaymentProvider);
    }

    @Test
    public void shouldResolveSandboxPaymentProvider() {
        PaymentProvider sandbox = providers.byName(PaymentGatewayName.SANDBOX);
        assertThat(sandbox, is(instanceOf(SandboxPaymentProvider.class)));
    }

    @Test
    public void shouldResolveWorldpayPaymentProvider() {
        PaymentProvider worldpay = providers.byName(PaymentGatewayName.WORLDPAY);
        assertThat(worldpay, is(instanceOf(WorldpayPaymentProvider.class)));
    }

    @Test
    public void shouldResolveSmartpayPaymentProvider() {
        PaymentProvider smartpay = providers.byName(PaymentGatewayName.SMARTPAY);
        assertThat(smartpay, is(instanceOf(SmartpayPaymentProvider.class)));
    }

    @Test
    public void shouldResolveEpdqPaymentProvider() {
        PaymentProvider epdq = providers.byName(EPDQ);
        assertThat(epdq, is(instanceOf(EpdqPaymentProvider.class)));
    }
}
