package uk.gov.pay.connector.service;

import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.gateway.model.status.InterpretedStatus;
import uk.gov.pay.connector.gateway.model.status.MappedRefundStatus;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void shouldThrowExceptionForGetChargeStatus() {
        assertThrows(IllegalStateException.class,() -> {
            mappedRefundStatus.getChargeStatus();
        });
    }

}
