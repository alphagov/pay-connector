package uk.gov.pay.connector.queue.tasks.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationType;
import uk.gov.service.payments.commons.api.json.ApiResponseInstantSerializer;

import java.time.Instant;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RetryPaymentOrRefundEmailTaskData {
    @JsonProperty("resource_external_id")
    private String resourceExternalId;
    @JsonProperty("email_notification_type")
    private EmailNotificationType emailNotificationType;

    @JsonProperty("failed_attempt_time")
    @JsonSerialize(using = ApiResponseInstantSerializer.class)
    private Instant failedAttemptTime;

    public RetryPaymentOrRefundEmailTaskData() {
        // empty
    }

    public RetryPaymentOrRefundEmailTaskData(String paymentOrRefundExternalId, EmailNotificationType emailNotificationType, Instant failedAttemptTime) {
        this.resourceExternalId = paymentOrRefundExternalId;
        this.emailNotificationType = emailNotificationType;
        this.failedAttemptTime = failedAttemptTime;
    }

    public static RetryPaymentOrRefundEmailTaskData of(String paymentOrRefundExternalId, EmailNotificationType emailNotificationType, Instant failedAttemptTime) {
        return new RetryPaymentOrRefundEmailTaskData(paymentOrRefundExternalId, emailNotificationType, failedAttemptTime);
    }

    public String getResourceExternalId() {
        return resourceExternalId;
    }

    public Instant getFailedAttemptTime() {
        return failedAttemptTime;
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
