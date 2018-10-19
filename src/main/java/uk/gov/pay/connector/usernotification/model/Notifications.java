package uk.gov.pay.connector.usernotification.model;

import com.google.common.collect.ImmutableList;
import org.apache.http.NameValuePair;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class Notifications<T> {

    private ImmutableList<Notification<T>> notifications;

    private Notifications(ImmutableList<Notification<T>> notifications) {
        this.notifications = notifications;
    }

    public static class Builder<T> {
        List<Notification<T>> notifications = new ArrayList<>();

        public Builder<T> addNotificationFor(String transactionId, String reference, T status, ZonedDateTime generationTime, List<NameValuePair> payload) {
            notifications.add(new BaseNotification<>(transactionId, reference, status, generationTime, payload));
            return this;
        }

        public Notifications<T> build() {
            return new Notifications<T>(ImmutableList.copyOf(notifications));
        }
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public ImmutableList<Notification<T>> get() {
        return notifications;
    }

    @Override
    public String toString() {
        return String.format("Notifications [notifications=%s]", get());
    }
}
