package uk.gov.pay.connector.service;

import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.gateway.model.status.InterpretedStatus;
import uk.gov.pay.connector.gateway.model.status.UnknownStatus;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UnknownStatusTest {

    private final UnknownStatus UnknownStatus = new UnknownStatus();

    @Test
    public void shouldReturnCorrectType() {
        assertThat(UnknownStatus.getType(), is(InterpretedStatus.Type.UNKNOWN));
    }


    @Test
    void shouldThrowExceptionForGetChargeStatus() {
        assertThrows(IllegalStateException.class, () -> UnknownStatus.getChargeStatus());
    }


    @Test
    void shouldThrowExceptionForGetRefundStatus() {
        assertThrows(IllegalStateException.class, () -> UnknownStatus.getRefundStatus());
    }
    
}
