package uk.gov.pay.connector.model;


import org.junit.Test;

import java.time.ZonedDateTime;
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
        ZonedDateTime now = ZonedDateTime.now();
        Object payload = "payload";

        BaseNotification baseNotification = new BaseNotification<>(transactionId, reference, status, now, payload);

        ExtendedNotification extendedNotification = ExtendedNotification.extend(baseNotification, internalStatus);

        assertThat(extendedNotification.getTransactionId(), is(transactionId));
        assertThat(extendedNotification.getStatus(), is(status));
        assertThat(extendedNotification.getInternalStatus(), is(internalStatus));
        assertThat(extendedNotification.isOfChargeType(), is(true));
        assertThat(extendedNotification.isOfRefundType(), is(false));
        assertThat(extendedNotification.getGatewayEventDate(), is(now));
        assertThat(extendedNotification.getPayload(), is(Optional.of(payload)));
    }

    @Test
    public void shouldCreateEnExtendedNotificationOfRefundStatusType() {
        String transactionId = "transaction-id";
        String reference = "reference";
        String status = "status";
        Optional<Enum> internalStatus = Optional.of(REFUNDED);
        ZonedDateTime now = ZonedDateTime.now();
        Object payload = "payload";

        BaseNotification baseNotification = new BaseNotification<>(transactionId, reference, status, now, payload);

        ExtendedNotification extendedNotification = ExtendedNotification.extend(baseNotification, internalStatus);

        assertThat(extendedNotification.getTransactionId(), is(transactionId));
        assertThat(extendedNotification.getStatus(), is(status));
        assertThat(extendedNotification.getInternalStatus(), is(internalStatus));
        assertThat(extendedNotification.isOfChargeType(), is(false));
        assertThat(extendedNotification.isOfRefundType(), is(true));
        assertThat(extendedNotification.getGatewayEventDate(), is(now));
        assertThat(extendedNotification.getPayload(), is(Optional.of(payload)));
    }

}
