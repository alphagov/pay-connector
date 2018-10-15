package uk.gov.pay.connector.service;

import org.junit.Test;
import uk.gov.pay.connector.gateway.model.status.InterpretedStatus;
import uk.gov.pay.connector.gateway.model.status.UnknownStatus;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class UnknownStatusTest {

    private final UnknownStatus UnknownStatus = new UnknownStatus();

    @Test
    public void shouldReturnCorrectType() {
        assertThat(UnknownStatus.getType(), is(InterpretedStatus.Type.UNKNOWN));
    }

    @Test(expected =  IllegalStateException.class)
    public void shouldThrowExceptionForGetChargeStatus() {
        UnknownStatus.getChargeStatus();
    }

    @Test(expected =  IllegalStateException.class)
    public void shouldThrowExceptionForGetRefundStatus() {
        UnknownStatus.getRefundStatus();
    }
    
}
