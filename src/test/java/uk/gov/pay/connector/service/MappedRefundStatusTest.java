package uk.gov.pay.connector.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.pay.connector.gateway.model.status.InterpretedStatus;
import uk.gov.pay.connector.gateway.model.status.MappedRefundStatus;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

class MappedRefundStatusTest {

    private final MappedRefundStatus mappedRefundStatus = new MappedRefundStatus(RefundStatus.REFUNDED);

    @Test
    void shouldReturnCorrectType() {
        assertThat(mappedRefundStatus.getType(), is(InterpretedStatus.Type.REFUND_STATUS));
    }

    @Test
    void shouldGetRefundStatus() {
        assertThat(mappedRefundStatus.getRefundStatus(), is(RefundStatus.REFUNDED));
    }

    @Test()
    void shouldThrowExceptionForGetChargeStatus() {

        Assertions.assertThrows(IllegalStateException.class,() -> {
            mappedRefundStatus.getChargeStatus();
        });
    }

}
