package uk.gov.pay.connector.model;

import org.junit.Test;
import uk.gov.pay.connector.chargeevents.model.TransactionEvent;
import uk.gov.pay.connector.model.api.ExternalChargeState;

import java.time.ZonedDateTime;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.chargeevents.model.TransactionEvent.Type;
import static uk.gov.pay.connector.chargeevents.model.TransactionEvent.extractState;
import static uk.gov.pay.connector.model.api.ExternalRefundStatus.EXTERNAL_SUBMITTED;

public class TransactionEventTest {

    public static final String USER_EXTERNAL_ID = "r378y387y8weriyi";

    @Test
    public void equals_shouldReturnTrue_whenSameInstance() {

        TransactionEvent event = new TransactionEvent(Type.PAYMENT, "charge", extractState(ExternalChargeState.EXTERNAL_CREATED), 100L, ZonedDateTime.now());

        assertThat(event.equals(event), is(true));
    }

    @Test
    public void equals_shouldReturnTrue_whenFieldsAreTheSame() {

        TransactionEvent event1 = new TransactionEvent(Type.PAYMENT, "charge", extractState(ExternalChargeState.EXTERNAL_CREATED), 100L, ZonedDateTime.now());
        TransactionEvent event2 = new TransactionEvent(Type.PAYMENT, "charge", extractState(ExternalChargeState.EXTERNAL_CREATED), 100L, ZonedDateTime.now());

        assertThat(event1.equals(event2), is(true));
    }

    @Test
    public void equals_shouldReturnFalse_whenFieldsRefundReferenceIsDifferent() {

        TransactionEvent event1 = new TransactionEvent(Type.REFUND, "charge", "success", extractState(EXTERNAL_SUBMITTED), 100L, ZonedDateTime.now(), USER_EXTERNAL_ID);
        TransactionEvent event2 = new TransactionEvent(Type.REFUND, "charge", "submitted", extractState(EXTERNAL_SUBMITTED), 100L, ZonedDateTime.now(),USER_EXTERNAL_ID);

        assertThat(event1.equals(event2), is(false));
    }

    @Test
    public void equals_shouldReturnFalse_whenFirstObjectRefundReferenceIsNull() {

        TransactionEvent event1 = new TransactionEvent(Type.REFUND, "charge", null, extractState(EXTERNAL_SUBMITTED), 100L, ZonedDateTime.now(),USER_EXTERNAL_ID);
        TransactionEvent event2 = new TransactionEvent(Type.REFUND, "charge", "submitted", extractState(EXTERNAL_SUBMITTED), 100L, ZonedDateTime.now(),USER_EXTERNAL_ID);

        assertThat(event1.equals(event2), is(false));
    }

    @Test
    public void equals_shouldReturnFalse_whenSecondObjectRefundReferenceIsNull() {

        TransactionEvent event1 = new TransactionEvent(Type.REFUND, null, "success", extractState(EXTERNAL_SUBMITTED), 100L, ZonedDateTime.now(),USER_EXTERNAL_ID);
        TransactionEvent event2 = new TransactionEvent(Type.REFUND, "charge", null, extractState(EXTERNAL_SUBMITTED), 100L, ZonedDateTime.now(),USER_EXTERNAL_ID);

        assertThat(event1.equals(event2), is(false));
    }

}
