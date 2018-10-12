package uk.gov.pay.connector.gateway.epdq;

import org.junit.Test;
import uk.gov.pay.connector.gateway.model.status.InterpretedStatus;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUBMITTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.EXPIRE_CANCEL_SUBMITTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.SYSTEM_CANCEL_SUBMITTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.USER_CANCEL_SUBMITTED;
import static uk.gov.pay.connector.model.domain.RefundStatus.REFUNDED;
import static uk.gov.pay.connector.model.domain.RefundStatus.REFUND_ERROR;
import static uk.gov.pay.connector.service.StatusFlow.EXPIRE_FLOW;
import static uk.gov.pay.connector.service.StatusFlow.SYSTEM_CANCELLATION_FLOW;
import static uk.gov.pay.connector.service.StatusFlow.USER_CANCELLATION_FLOW;

public class EpdqStatusMapperTest {

    @Test
    public void shouldReturnAuthorisationRejectedStatusFromValue2() throws Exception {
        InterpretedStatus status = EpdqStatusMapper.get().from("2", AUTHORISATION_SUBMITTED);

        assertThat(status.getType(), is(InterpretedStatus.Type.CHARGE_STATUS));
        assertThat(status.getChargeStatus(), is(AUTHORISATION_REJECTED));
    }

    @Test
    public void shouldReturnAuthorisationSuccessStatusFromValue5() throws Exception {
        InterpretedStatus status = EpdqStatusMapper.get().from("5", AUTHORISATION_SUBMITTED);

        assertThat(status.getType(), is(InterpretedStatus.Type.CHARGE_STATUS));
        assertThat(status.getChargeStatus(), is(AUTHORISATION_SUCCESS));
    }

    @Test
    public void shouldReturnUserCancelledFromValue6WhenCurrentStatusUserCancelSubmitted() throws Exception {
        InterpretedStatus status = EpdqStatusMapper.get().from("6", USER_CANCEL_SUBMITTED);

        assertThat(status.getType(), is(InterpretedStatus.Type.CHARGE_STATUS));
        assertThat(status.getChargeStatus(), is(USER_CANCELLATION_FLOW.getSuccessTerminalState()));
    }

    @Test
    public void shouldReturnUserCancelledFromValue6WhenCurrentStatusSystemCancelSubmitted() throws Exception {
        InterpretedStatus status = EpdqStatusMapper.get().from("6", SYSTEM_CANCEL_SUBMITTED);

        assertThat(status.getType(), is(InterpretedStatus.Type.CHARGE_STATUS));
        assertThat(status.getChargeStatus(), is(SYSTEM_CANCELLATION_FLOW.getSuccessTerminalState()));
    }

    @Test
    public void shouldReturnUserCancelledFromValue6WhenCurrentStatusExpireCancelSubmitted() throws Exception {
        InterpretedStatus status = EpdqStatusMapper.get().from("6", EXPIRE_CANCEL_SUBMITTED);

        assertThat(status.getType(), is(InterpretedStatus.Type.CHARGE_STATUS));
        assertThat(status.getChargeStatus(), is(EXPIRE_FLOW.getSuccessTerminalState()));
    }

    @Test
    public void shouldReturnUserCancelledFromValue6WhenCurrentStatusCreated() throws Exception {
        InterpretedStatus status = EpdqStatusMapper.get().from("6", CREATED);

        assertThat(status.getType(), is(InterpretedStatus.Type.CHARGE_STATUS));
        assertThat(status.getChargeStatus(), is(SYSTEM_CANCELLATION_FLOW.getSuccessTerminalState()));
    }

    @Test
    public void shouldReturnUserCancelledFromValue6WhenCurrentStatusEnteringCardDetails() throws Exception {
        InterpretedStatus status = EpdqStatusMapper.get().from("6", ENTERING_CARD_DETAILS);

        assertThat(status.getType(), is(InterpretedStatus.Type.CHARGE_STATUS));
        assertThat(status.getChargeStatus(), is(SYSTEM_CANCELLATION_FLOW.getSuccessTerminalState()));
    }

    @Test
    public void shouldReturnCapturedStatusFromValue9() throws Exception {
        InterpretedStatus status = EpdqStatusMapper.get().from("9", CAPTURE_SUBMITTED);

        assertThat(status.getType(), is(InterpretedStatus.Type.CHARGE_STATUS));
        assertThat(status.getChargeStatus(), is(CAPTURED));
    }

    @Test
    public void shouldReturnRefundedStatusFromValue8() {
        InterpretedStatus status = EpdqStatusMapper.get().from("8", CAPTURED);

        assertThat(status.getType(), is(InterpretedStatus.Type.REFUND_STATUS));
        assertThat(status.getRefundStatus(), is(REFUNDED));
    }

    @Test
    public void shouldReturnRefundErrorStatusFromValue83() {

        InterpretedStatus status = EpdqStatusMapper.get().from("83", CAPTURED);

        assertThat(status.getType(), is(InterpretedStatus.Type.REFUND_STATUS));
        assertThat(status.getRefundStatus(), is(REFUND_ERROR));
    }

    @Test
    public void shouldReturnRefundedStatusFromValue7() {
        InterpretedStatus status = EpdqStatusMapper.get().from("7", CAPTURE_SUBMITTED);

        assertThat(status.getType(), is(InterpretedStatus.Type.REFUND_STATUS));
        assertThat(status.getRefundStatus(), is(REFUNDED));
    }

    @Test
    public void shouldReturnRefundErrorStatusFromValue73() {
        InterpretedStatus status = EpdqStatusMapper.get().from("73", CAPTURE_SUBMITTED);

        assertThat(status.getType(), is(InterpretedStatus.Type.REFUND_STATUS));
        assertThat(status.getRefundStatus(), is(REFUND_ERROR));
    }

    @Test
    public void shouldReturnUnknownStatusFromUnknownValue() throws Exception {
        InterpretedStatus status = EpdqStatusMapper.get().from("unknown", AUTHORISATION_SUCCESS);

        assertThat(status.getType(), is(InterpretedStatus.Type.UNKNOWN));
    }

    @Test
    public void shouldReturnRefundErrorStatusFromValue94() {
        InterpretedStatus status = EpdqStatusMapper.get().from("94", CAPTURED);

        assertThat(status.getType(), is(InterpretedStatus.Type.REFUND_STATUS));
        assertThat(status.getRefundStatus(), is(REFUND_ERROR));
    }
}
