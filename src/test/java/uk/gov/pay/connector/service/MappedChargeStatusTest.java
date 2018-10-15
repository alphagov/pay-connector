package uk.gov.pay.connector.service;

import org.junit.Test;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.gateway.model.status.InterpretedStatus;
import uk.gov.pay.connector.gateway.model.status.MappedChargeStatus;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class MappedChargeStatusTest {

    private final MappedChargeStatus mappedChargeStatus = new MappedChargeStatus(ChargeStatus.CAPTURED);

    @Test
    public void shouldReturnCorrectType() {
        assertThat(mappedChargeStatus.getType(), is(InterpretedStatus.Type.CHARGE_STATUS));
    }

    @Test
    public void shouldGetChargeStatus() {
        assertThat(mappedChargeStatus.getChargeStatus(), is(ChargeStatus.CAPTURED));
    }

    @Test(expected =  IllegalStateException.class)
    public void shouldThrowExceptionForGetRefundStatus() {
        mappedChargeStatus.getRefundStatus();
    }

}
