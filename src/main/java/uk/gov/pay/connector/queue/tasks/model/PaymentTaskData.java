package uk.gov.pay.connector.queue.tasks.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentTaskData {
    
    @JsonProperty("payment_external_id")
    private String paymentExternalId;

    public PaymentTaskData() {
        // empty
    }

    public PaymentTaskData(String paymentExternalId) {
        this.paymentExternalId = paymentExternalId;
    }

    public String getPaymentExternalId() {
        return paymentExternalId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PaymentTaskData that = (PaymentTaskData) o;
        return Objects.equals(paymentExternalId, that.paymentExternalId);
    }
}
