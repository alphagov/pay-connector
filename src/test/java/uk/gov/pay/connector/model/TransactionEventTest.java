package uk.gov.pay.connector.model;

import org.junit.Test;
import uk.gov.pay.connector.chargeevent.model.TransactionEvent;
import uk.gov.pay.connector.common.model.api.ExternalChargeState;

import java.time.Instant;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static uk.gov.pay.connector.chargeevent.model.TransactionEvent.Type;
import static uk.gov.pay.connector.chargeevent.model.TransactionEvent.extractState;
import static uk.gov.pay.connector.common.model.api.ExternalRefundStatus.EXTERNAL_SUBMITTED;

public class TransactionEventTest {

    private static final String USER_EXTERNAL_ID = "r378y387y8weriyi";

    @Test
    public void equals_shouldReturnTrue_whenSameInstance() {

        TransactionEvent event = new TransactionEvent(Type.PAYMENT, "charge", extractState(ExternalChargeState.EXTERNAL_CREATED),
                100L, Instant.now());

        assertEquals(event, event);
    }

    @Test
    public void equals_shouldReturnTrue_whenFieldsAreTheSame() {

        TransactionEvent event1 = new TransactionEvent(Type.PAYMENT, "charge", extractState(ExternalChargeState.EXTERNAL_CREATED),
                100L, Instant.now());
        TransactionEvent event2 = new TransactionEvent(Type.PAYMENT, "charge", extractState(ExternalChargeState.EXTERNAL_CREATED),
                100L, Instant.now());

        assertThat(event1.equals(event2), is(true));
    }

    @Test
    public void equals_shouldReturnFalse_whenFieldsRefundTransactionIdIsDifferent() {

        TransactionEvent event1 = new TransactionEvent(Type.REFUND, "charge", "success", extractState(EXTERNAL_SUBMITTED),
                100L, Instant.now(), USER_EXTERNAL_ID);
        TransactionEvent event2 = new TransactionEvent(Type.REFUND, "charge", "submitted", extractState(EXTERNAL_SUBMITTED),
                100L, Instant.now(),USER_EXTERNAL_ID);

        assertThat(event1.equals(event2), is(false));
    }

    @Test
    public void equals_shouldReturnFalse_whenFirstObjectRefundTransactionIdIsNull() {

        TransactionEvent event1 = new TransactionEvent(Type.REFUND, "charge", null, extractState(EXTERNAL_SUBMITTED),
                100L, Instant.now(),USER_EXTERNAL_ID);
        TransactionEvent event2 = new TransactionEvent(Type.REFUND, "charge", "submitted", extractState(EXTERNAL_SUBMITTED),
                100L, Instant.now(),USER_EXTERNAL_ID);

        assertThat(event1.equals(event2), is(false));
    }

    @Test
    public void equals_shouldReturnFalse_whenSecondObjectRefundTransactionIdIsNull() {

        TransactionEvent event1 = new TransactionEvent(Type.REFUND, null, "success", extractState(EXTERNAL_SUBMITTED),
                100L, Instant.now(),USER_EXTERNAL_ID);
        TransactionEvent event2 = new TransactionEvent(Type.REFUND, "charge", null, extractState(EXTERNAL_SUBMITTED),
                100L, Instant.now(),USER_EXTERNAL_ID);

        assertThat(event1.equals(event2), is(false));
    }

}
