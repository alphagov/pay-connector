package uk.gov.pay.connector.gateway;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentGatewayNameTest {
    
    @Test
    void shouldReturnPaymentGatewayName() {
        assertEquals(PaymentGatewayName.SANDBOX, PaymentGatewayName.valueFrom("sandbox"));
        assertEquals(PaymentGatewayName.STRIPE, PaymentGatewayName.valueFrom("stripe"));
    }
    
    @Test
    void shouldThrowExceptionIfInvalidNameSupplied() {
        assertThrows(PaymentGatewayName.Unsupported.class, () -> PaymentGatewayName.valueFrom("blah"));
    }
}
