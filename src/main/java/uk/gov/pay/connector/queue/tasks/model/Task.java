package uk.gov.pay.connector.queue.tasks.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import uk.gov.pay.connector.queue.tasks.TaskType;

import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "data", "task", "payment_external_id" })
public class Task {
    
    private String data;
    @JsonProperty("task")
    private TaskType taskType;
    
    // back compat - to remove
    @JsonProperty("payment_external_id")
    private String paymentExternalId; 
    
    public Task() {
        // empty
    }

    public Task(String data, TaskType taskType) {
        this.data = data;
        this.taskType = taskType;
    }

    public String getData() {
        return data;
    }

    public TaskType getTaskType() {
        return taskType;
    }

    // back compat - to remove
    public String getPaymentExternalId() {
        return paymentExternalId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Task that = (Task) o;
        return Objects.equals(data, that.data) && Objects.equals(taskType, that.taskType) && Objects.equals(paymentExternalId, that.paymentExternalId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(data, taskType, paymentExternalId);
    }
}
