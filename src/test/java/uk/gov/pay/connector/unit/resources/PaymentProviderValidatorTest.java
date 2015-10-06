package uk.gov.pay.connector.unit.resources;

import org.junit.Test;
import uk.gov.pay.connector.resources.PaymentProviderValidator;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PaymentProviderValidatorTest {

    @Test
    public void shouldValidateSmartpayProvider() throws Exception {
        assertTrue(PaymentProviderValidator.isValidProvider("smartpay"));
    }

    @Test
    public void shouldValidateWorldpayProvider() throws Exception {
        assertTrue(PaymentProviderValidator.isValidProvider("worldpay"));
    }

    @Test
    public void shouldValidateSandboxProvider() throws Exception {
        assertTrue(PaymentProviderValidator.isValidProvider("sandbox"));
    }

    @Test
    public void shouldNotValidateRandomProvider() throws Exception {
        assertFalse(PaymentProviderValidator.isValidProvider("lskdj"));
    }
}