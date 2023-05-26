package uk.gov.pay.connector.model.domain;

import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.common.model.api.ExternalRefundStatus;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

class RefundStatusMapTest {

    @Test
    void shouldHaveCorrectNumberOfStatuses() {
        assertThat(RefundStatus.values().length, is(4));
    }

    @Test
    void shouldMapFromRefundStatusToExternalStatus() {
        assertThat(RefundStatus.CREATED.toExternal(), is(ExternalRefundStatus.EXTERNAL_SUBMITTED));
        assertThat(RefundStatus.REFUND_ERROR.toExternal(), is(ExternalRefundStatus.EXTERNAL_ERROR));
        assertThat(RefundStatus.REFUND_SUBMITTED.toExternal(), is(ExternalRefundStatus.EXTERNAL_SUBMITTED));
        assertThat(RefundStatus.REFUNDED.toExternal(), is(ExternalRefundStatus.EXTERNAL_SUCCESS));
    }

    @Test
    void shouldGetStatusValue() {
        assertThat(RefundStatus.CREATED.getValue(), is("CREATED"));
        assertThat(RefundStatus.REFUND_ERROR.getValue(), is("REFUND ERROR"));
        assertThat(RefundStatus.REFUND_SUBMITTED.getValue(), is("REFUND SUBMITTED"));
        assertThat(RefundStatus.REFUNDED.getValue(), is("REFUNDED"));
    }

    @Test
    void shouldGetStatusValueWhenToString() {
        assertThat(RefundStatus.CREATED.toString(), is("CREATED"));
        assertThat(RefundStatus.REFUND_ERROR.toString(), is("REFUND ERROR"));
        assertThat(RefundStatus.REFUND_SUBMITTED.toString(), is("REFUND SUBMITTED"));
        assertThat(RefundStatus.REFUNDED.toString(), is("REFUNDED"));
    }

    @Test
    void shouldGetEnumFromStringValue() {
        assertThat(RefundStatus.CREATED, is(RefundStatus.fromString("CREATED")));
        assertThat(RefundStatus.REFUND_SUBMITTED, is(RefundStatus.fromString("REFUND SUBMITTED")));
        assertThat(RefundStatus.REFUND_ERROR, is(RefundStatus.fromString("REFUND ERROR")));
        assertThat(RefundStatus.REFUNDED, is(RefundStatus.fromString("REFUNDED")));
    }
}
