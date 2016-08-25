package uk.gov.pay.connector.model;

import org.junit.Test;

import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class NotificationsTest {

    @Test
    public void shouldBuildNotifications() {
        Notifications<String> notifications = Notifications
                .<String>builder()
                .addNotificationFor("transaction-id-1", "reference-1", "status-1")
                .addNotificationFor("transaction-id-2", "reference-2", "status-2")
                .build();

        assertThat(notifications.get(), is(notNullValue()));
        assertThat(notifications.get().size(), is(2));
        assertThat(notifications.get().get(0).getTransactionId(), is("transaction-id-1"));
        assertThat(notifications.get().get(0).getReference(), is("reference-1"));
        assertThat(notifications.get().get(0).getStatus(), is("status-1"));
        assertThat(notifications.get().get(1).getTransactionId(), is("transaction-id-2"));
        assertThat(notifications.get().get(1).getReference(), is("reference-2"));
        assertThat(notifications.get().get(1).getStatus(), is("status-2"));
    }

}
