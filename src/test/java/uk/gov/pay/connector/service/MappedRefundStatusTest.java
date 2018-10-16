package uk.gov.pay.connector.service;

import org.junit.Test;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.pay.connector.gateway.model.status.InterpretedStatus;
import uk.gov.pay.connector.gateway.model.status.MappedRefundStatus;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class MappedRefundStatusTest {

    private final MappedRefundStatus mappedRefundStatus = new MappedRefundStatus(RefundStatus.REFUNDED);

    @Test
    public void shouldReturnCorrectType() {
        assertThat(mappedRefundStatus.getType(), is(InterpretedStatus.Type.REFUND_STATUS));
    }

    @Test
    public void shouldGetRefundStatus() {
        assertThat(mappedRefundStatus.getRefundStatus(), is(RefundStatus.REFUNDED));
    }

    @Test(expected =  IllegalStateException.class)
    public void shouldThrowExceptionForGetChargeStatus() {
        mappedRefundStatus.getChargeStatus();
    }

}
