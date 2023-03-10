package uk.gov.pay.connector.queue.tasks.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DeleteStoredPaymentDetailsTaskData {
    @JsonProperty("agreement_external_id")
    private String agreementExternalId;

    @JsonProperty("paymentInstrument_external_id")
    private String paymentInstrumentExternalId;
    
    public DeleteStoredPaymentDetailsTaskData() {
        // empty
    }

    public DeleteStoredPaymentDetailsTaskData(String agreementExternalId, String paymentInstrumentExternalId) {
        this.agreementExternalId = agreementExternalId;
        this.paymentInstrumentExternalId = paymentInstrumentExternalId;
    }

    public String getAgreementExternalId() {
        return agreementExternalId;
    }

    public String getPaymentInstrumentExternalId() {
        return paymentInstrumentExternalId;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DeleteStoredPaymentDetailsTaskData that = (DeleteStoredPaymentDetailsTaskData) o;
        return Objects.equals(agreementExternalId, that.agreementExternalId) && Objects.equals(paymentInstrumentExternalId, that.paymentInstrumentExternalId);
    }
}
