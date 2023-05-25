package uk.gov.pay.connector.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.gateway.model.status.InterpretedStatus;
import uk.gov.pay.connector.gateway.model.status.UnknownStatus;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

class UnknownStatusTest {

    private final UnknownStatus UnknownStatus = new UnknownStatus();

    @Test
    public void shouldReturnCorrectType() {
        assertThat(UnknownStatus.getType(), is(InterpretedStatus.Type.UNKNOWN));
    }

    @Test()
    void shouldThrowExceptionForGetChargeStatus() {

        Assertions.assertThrows(IllegalStateException.class, () -> {
            UnknownStatus.getChargeStatus();
        });
    }

    @Test()
    void shouldThrowExceptionForGetRefundStatus() {
        Assertions.assertThrows(IllegalStateException.class, () -> {
            UnknownStatus.getRefundStatus();
        });
    }
    
}
