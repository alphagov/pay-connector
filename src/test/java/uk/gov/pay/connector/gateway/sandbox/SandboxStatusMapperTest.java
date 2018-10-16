package uk.gov.pay.connector.gateway.sandbox;

import org.junit.Test;
import uk.gov.pay.connector.gateway.model.status.InterpretedStatus;
import uk.gov.pay.connector.gateway.model.status.MappedChargeStatus;
import uk.gov.pay.connector.gateway.model.status.MappedRefundStatus;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.model.domain.RefundStatus.REFUNDED;

public class SandboxStatusMapperTest {

    @Test
    public void shouldGetExpectedStatus_when_AUTHORISED() {
        InterpretedStatus status = SandboxStatusMapper.get().from("AUTHORISED", AUTHORISATION_SUCCESS);

        assertThat(status.getType(), is(InterpretedStatus.Type.IGNORED));
    }

    @Test
    public void shouldGetExpectedStatus_when_CAPTURED() {
        InterpretedStatus status = SandboxStatusMapper.get().from("CAPTURED", CAPTURE_SUBMITTED);

        assertThat(status.getType(), is(InterpretedStatus.Type.CHARGE_STATUS));
        assertThat(((MappedChargeStatus) status).getChargeStatus(), is(CAPTURED));
    }

    @Test
    public void shouldGetExpectedStatus_when_REFUNDED() {
        InterpretedStatus status =  SandboxStatusMapper.get().from("REFUNDED", CAPTURED);

        assertThat(status.getType(), is(InterpretedStatus.Type.REFUND_STATUS));
        assertThat(((MappedRefundStatus) status).getRefundStatus(), is(REFUNDED));
    }

    @Test
    public void shouldGetExpectedStatus_when_unknownValue() {
        InterpretedStatus status = SandboxStatusMapper.get().from("whatever", AUTHORISATION_SUBMITTED);

        assertThat(status.getType(), is(InterpretedStatus.Type.UNKNOWN));
    }
}
