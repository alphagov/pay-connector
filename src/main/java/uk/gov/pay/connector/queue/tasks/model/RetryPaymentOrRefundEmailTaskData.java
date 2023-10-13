package uk.gov.pay.connector.queue.tasks.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationType;

import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RetryPaymentOrRefundEmailTaskData {
    @JsonProperty("resource_external_id")
    private String resourceExternalId;
    @JsonProperty("email_notification_type")
    private EmailNotificationType emailNotificationType;

    public RetryPaymentOrRefundEmailTaskData() {
        // empty
    }

    public RetryPaymentOrRefundEmailTaskData(String paymentOrRefundExternalId, EmailNotificationType emailNotificationType) {
        this.resourceExternalId = paymentOrRefundExternalId;
        this.emailNotificationType = emailNotificationType;
    }

    public static RetryPaymentOrRefundEmailTaskData of(String paymentOrRefundExternalId, EmailNotificationType emailNotificationType) {
        return new RetryPaymentOrRefundEmailTaskData(paymentOrRefundExternalId, emailNotificationType);
    }

    public String getResourceExternalId() {
        return resourceExternalId;
    }

    public EmailNotificationType getEmailNotificationType() {
        return emailNotificationType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RetryPaymentOrRefundEmailTaskData that = (RetryPaymentOrRefundEmailTaskData) o;
        return Objects.equals(resourceExternalId, that.resourceExternalId) && emailNotificationType == that.emailNotificationType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(resourceExternalId, emailNotificationType);
    }
}
