package uk.gov.pay.connector.service.epdq;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.model.domain.RefundStatus.REFUNDED;

import org.junit.Test;
import uk.gov.pay.connector.service.InterpretedStatus;
import uk.gov.pay.connector.service.worldpay.WorldpayStatusMapper;

public class EpdqStatusMapperTest {

    @Test
    public void shouldReturnAuthorisationRejectedStatusFromValue2() throws Exception {
        InterpretedStatus status = EpdqStatusMapper.get().from("2");

        assertThat(status.isMapped(), is(true));
        assertThat(status.isIgnored(), is(false));
        assertThat(status.isUnknown(), is(false));
        assertThat(status.isDeferred(), is(false));
        assertThat(status.get().get(), is(AUTHORISATION_REJECTED));
    }

    @Test
    public void shouldReturnAuthorisationSuccessStatusFromValue5() throws Exception {
        InterpretedStatus status = EpdqStatusMapper.get().from("5");

        assertThat(status.isMapped(), is(true));
        assertThat(status.isIgnored(), is(false));
        assertThat(status.isUnknown(), is(false));
        assertThat(status.isDeferred(), is(false));
        assertThat(status.get().get(), is(AUTHORISATION_SUCCESS));
    }

    @Test
    public void shouldDeferStatusFromValue6() throws Exception {
        InterpretedStatus status = EpdqStatusMapper.get().from("6");
        assertThat(status.isMapped(), is(false));
        assertThat(status.isIgnored(), is(false));
        assertThat(status.isUnknown(), is(false));
        assertThat(status.isDeferred(), is(true));
    }

    @Test
    public void shouldReturnCapturedStatusFromValue9() throws Exception {
        InterpretedStatus status = EpdqStatusMapper.get().from("9");

        assertThat(status.isMapped(), is(true));
        assertThat(status.isIgnored(), is(false));
        assertThat(status.isUnknown(), is(false));
        assertThat(status.isDeferred(), is(false));
        assertThat(status.get().get(), is(CAPTURED));
    }

    @Test
    public void shouldReturnUnknownStatusFromUnknownValue() throws Exception {
        InterpretedStatus status = EpdqStatusMapper.get().from("unknown");

        assertThat(status.isMapped(), is(false));
        assertThat(status.isIgnored(), is(false));
        assertThat(status.isUnknown(), is(true));
        assertThat(status.isDeferred(), is(false));
    }
}
