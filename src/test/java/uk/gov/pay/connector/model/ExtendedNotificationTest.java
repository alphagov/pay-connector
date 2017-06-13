package uk.gov.pay.connector.model;


import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.junit.Test;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.RefundStatus;
import uk.gov.pay.connector.service.BaseStatusMapper;
import uk.gov.pay.connector.service.BaseStatusMapper.MappedStatus;

import static java.util.Collections.singletonList;
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
        MappedStatus mappedStatus = new MappedStatus(ChargeStatus.CAPTURED);
        ZonedDateTime now = ZonedDateTime.now();
        List<NameValuePair> payload = singletonList(new BasicNameValuePair("my", "payload"));

        BaseNotification baseNotification = new BaseNotification<>(transactionId, reference, status, now, payload);

        ExtendedNotification extendedNotification = ExtendedNotification.extend(baseNotification, mappedStatus);

        assertThat(extendedNotification.getTransactionId(), is(transactionId));
        assertThat(extendedNotification.getStatus(), is(status));
        assertThat(extendedNotification.getInterpretedStatus(), is(mappedStatus));
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
        MappedStatus mappedStatus = new MappedStatus(RefundStatus.REFUNDED);
        ZonedDateTime now = ZonedDateTime.now();
        List<NameValuePair> payload = singletonList(new BasicNameValuePair("my", "payload"));

        BaseNotification baseNotification = new BaseNotification<>(transactionId, reference, status, now, payload);

        ExtendedNotification extendedNotification = ExtendedNotification.extend(baseNotification, mappedStatus);

        assertThat(extendedNotification.getTransactionId(), is(transactionId));
        assertThat(extendedNotification.getStatus(), is(status));
        assertThat(extendedNotification.getInterpretedStatus(), is(mappedStatus));
        assertThat(extendedNotification.isOfChargeType(), is(false));
        assertThat(extendedNotification.isOfRefundType(), is(true));
        assertThat(extendedNotification.getGatewayEventDate(), is(now));
        assertThat(extendedNotification.getPayload(), is(Optional.of(payload)));
    }

}
