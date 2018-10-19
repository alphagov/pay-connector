package uk.gov.pay.connector.usernotification.model;

import org.apache.http.message.BasicNameValuePair;
import org.junit.Test;

import java.time.ZonedDateTime;
import java.util.Optional;

import static java.util.Collections.singletonList;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class NotificationsTest {

    @Test
    public void shouldBuildNotifications() {
        ZonedDateTime now = ZonedDateTime.now();

        Notifications<String> notifications = Notifications
                .<String>builder()
                .addNotificationFor("transaction-id-1", "reference-1", "status-1", now,
                        singletonList(new BasicNameValuePair("my", "payload1")))
                .addNotificationFor("transaction-id-2", "reference-2", "status-2", now,
                        singletonList(new BasicNameValuePair("my", "payload2")))
                .build();

        assertThat(notifications.get(), is(notNullValue()));
        assertThat(notifications.get().size(), is(2));

        assertThat(notifications.get().get(0).getTransactionId(), is("transaction-id-1"));
        assertThat(notifications.get().get(0).getReference(), is("reference-1"));
        assertThat(notifications.get().get(0).getStatus(), is("status-1"));
        assertThat(notifications.get().get(0).getGatewayEventDate(), is(now));
        assertThat(notifications.get().get(0).getPayload(), is(Optional.of(singletonList(new BasicNameValuePair("my", "payload1")))));

        assertThat(notifications.get().get(1).getTransactionId(), is("transaction-id-2"));
        assertThat(notifications.get().get(1).getReference(), is("reference-2"));
        assertThat(notifications.get().get(1).getStatus(), is("status-2"));
        assertThat(notifications.get().get(1).getGatewayEventDate(), is(now));
        assertThat(notifications.get().get(1).getPayload(), is(Optional.of(singletonList(new BasicNameValuePair("my", "payload2")))));
    }

    @Test
    public void shouldConvertToString() {
        ZonedDateTime now = ZonedDateTime.now();

        Notifications<String> notifications = Notifications
                .<String>builder()
                .addNotificationFor("transaction-id-1", "reference-1", "status-1", now, singletonList(new BasicNameValuePair("my", "payload1")))
                .addNotificationFor("transaction-id-2", "reference-2", "status-2", now, singletonList(new BasicNameValuePair("my", "payload2")))
                .build();

        assertThat(notifications.toString(),
                is(String.format("Notifications [notifications=" +
                        "[Notification [reference=reference-1, transactionId=transaction-id-1, status=status-1, gatewayEventDate=%s], " +
                        "Notification [reference=reference-2, transactionId=transaction-id-2, status=status-2, gatewayEventDate=%s]]]",
                        now.toString(), now.toString())));
    }

}
