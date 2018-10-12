package uk.gov.pay.connector.service;

import org.junit.Test;
import uk.gov.pay.connector.gateway.model.status.IgnoredStatus;
import uk.gov.pay.connector.gateway.model.status.InterpretedStatus;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class IgnoredStatusTest {

    private final IgnoredStatus ignoredStatus = new IgnoredStatus();

    @Test
    public void shouldReturnCorrectType() {
        assertThat(ignoredStatus.getType(), is(InterpretedStatus.Type.IGNORED));
    }

    @Test(expected =  IllegalStateException.class)
    public void shouldThrowExceptionForGetChargeStatus() {
        ignoredStatus.getChargeStatus();
    }

    @Test(expected =  IllegalStateException.class)
    public void shouldThrowExceptionForGetRefundStatus() {
        ignoredStatus.getRefundStatus();
    }

}
