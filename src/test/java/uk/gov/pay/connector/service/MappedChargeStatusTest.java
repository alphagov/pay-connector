package uk.gov.pay.connector.service;

import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gateway.model.status.InterpretedStatus;
import uk.gov.pay.connector.gateway.model.status.MappedChargeStatus;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MappedChargeStatusTest {

    private final MappedChargeStatus mappedChargeStatus = new MappedChargeStatus(ChargeStatus.CAPTURED);

    @Test
    void shouldReturnCorrectType() {
        assertThat(mappedChargeStatus.getType(), is(InterpretedStatus.Type.CHARGE_STATUS));
    }

    @Test
    void shouldGetChargeStatus() {
        assertThat(mappedChargeStatus.getChargeStatus(), is(ChargeStatus.CAPTURED));
    }

    @Test
    void shouldThrowExceptionForGetRefundStatus() {
        assertThrows(IllegalStateException.class, () -> {
            mappedChargeStatus.getRefundStatus();
        });
    }

}
