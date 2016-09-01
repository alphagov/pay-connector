package uk.gov.pay.connector.model;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;

public class Notifications<T> {

    private ImmutableList<Notification<T>> notifications;

    private Notifications(ImmutableList<Notification<T>> notifications) {
        this.notifications = notifications;
    }

    public static class Builder<T> {
        List<Notification<T>> notifications = new ArrayList<>();

        public Builder<T> addNotificationFor(String transactionId, String reference, T status) {
            notifications.add(new BaseNotification(transactionId, reference, status));
            return this;
        }

        public Notifications<T> build() {
            return new Notifications(ImmutableList.copyOf(notifications));
        }
    }

    public static <T> Builder<T> builder() {
        return new Builder();
    }

    public ImmutableList<Notification<T>> get() {
        return notifications;
    }


}
