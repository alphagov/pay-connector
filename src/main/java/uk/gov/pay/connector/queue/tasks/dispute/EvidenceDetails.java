package uk.gov.pay.connector.queue.tasks.dispute;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EvidenceDetails {
    @JsonProperty("due_by")
    private Long dueByTimestamp;

    public EvidenceDetails() {
        // for jackson
    }

    public EvidenceDetails(Long dueByTimestamp) {
        this.dueByTimestamp = dueByTimestamp;
    }

    public Long getDueByTimestamp() {
        return dueByTimestamp;
    }
}
