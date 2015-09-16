package uk.gov.pay.connector.unit.service;

import org.junit.Test;
import uk.gov.pay.connector.service.PaymentProvider;
import uk.gov.pay.connector.service.PaymentProviderFactory;
import uk.gov.pay.connector.service.sandbox.SandboxPaymentProvider;
import uk.gov.pay.connector.service.worldpay.WorldpayPaymentProvider;

import java.util.Optional;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class PaymentProviderFactoryTest {

    @Test
    public void shouldResolveSandboxPaymentProvider() throws Exception {
        Optional<PaymentProvider> maybeSandbox = PaymentProviderFactory.resolve("sandbox");
        assertTrue(maybeSandbox.isPresent());
        assertThat(maybeSandbox.get(), is(instanceOf(SandboxPaymentProvider.class)));
    }

    @Test
    public void shouldResolveWorldpayPaymentProvider() throws Exception {
        Optional<PaymentProvider> mayBeWorldpay = PaymentProviderFactory.resolve("worldpay");
        assertTrue(mayBeWorldpay.isPresent());
        assertThat(mayBeWorldpay.get(), is(instanceOf(WorldpayPaymentProvider.class)));
    }

    @Test
    public void shouldResolveToEmptyForUnknownPaymentProvider() throws Exception {
        Optional<PaymentProvider> mayBePaymentProvider= PaymentProviderFactory.resolve("providerNotImplemented");
        assertThat(mayBePaymentProvider, is(Optional.empty()));
    }
}