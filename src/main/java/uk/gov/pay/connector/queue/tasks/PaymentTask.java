package uk.gov.pay.connector.queue.tasks;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PaymentTask {

    private String paymentExternalId;
    private String task;

    public PaymentTask() {
    }

    public PaymentTask(String paymentExternalId, String task) {
        this.paymentExternalId = paymentExternalId;
        this.task = task;
    }

    public String getPaymentExternalId() {
        return paymentExternalId;
    }

    public String getTask() {
        return task;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PaymentTask that = (PaymentTask) o;
        return Objects.equals(paymentExternalId, that.paymentExternalId) && Objects.equals(task, that.task);
    }

    @Override
    public int hashCode() {
        return Objects.hash(paymentExternalId, task);
    }
}
