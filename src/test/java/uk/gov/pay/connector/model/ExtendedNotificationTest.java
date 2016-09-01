package uk.gov.pay.connector.model;


import org.junit.Test;

import java.util.Optional;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.model.domain.RefundStatus.REFUNDED;

public class ExtendedNotificationTest {

    @Test
    public void shouldCreateEnExtendedNotificationOfChargeStatusType() {
        String transactionId = "transaction-id";
        String reference = "reference";
        String status = "status";
        Optional<Enum> internalStatus = Optional.of(CAPTURED);

        BaseNotification baseNotification = new BaseNotification<>(transactionId, reference, status);

        ExtendedNotification extendedNotification = ExtendedNotification.extend(baseNotification, internalStatus);

        assertThat(extendedNotification.getTransactionId(), is(transactionId));
        assertThat(extendedNotification.getStatus(), is(status));
        assertThat(extendedNotification.getInternalStatus(), is(internalStatus));
        assertThat(extendedNotification.isOfChargeType(), is(true));
        assertThat(extendedNotification.isOfRefundType(), is(false));
    }

    @Test
    public void shouldCreateEnExtendedNotificationOfRefundStatusType() {
        String transactionId = "transaction-id";
        String reference = "reference";
        String status = "status";
        Optional<Enum> internalStatus = Optional.of(REFUNDED);

        BaseNotification baseNotification = new BaseNotification<>(transactionId, reference, status);

        ExtendedNotification extendedNotification = ExtendedNotification.extend(baseNotification, internalStatus);

        assertThat(extendedNotification.getTransactionId(), is(transactionId));
        assertThat(extendedNotification.getStatus(), is(status));
        assertThat(extendedNotification.getInternalStatus(), is(internalStatus));
        assertThat(extendedNotification.isOfChargeType(), is(false));
        assertThat(extendedNotification.isOfRefundType(), is(true));
    }

}
