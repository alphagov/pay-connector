package uk.gov.pay.connector.client.ledger.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Objects;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Exemption {

    private boolean requested;
    private String type;
    private ExemptionOutcome outcome;

    public boolean isRequested() {
        return requested;
    }

    public void setRequested(boolean requested) {
        this.requested = requested;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public ExemptionOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(ExemptionOutcome outcome) {
        this.outcome = outcome;
    }

    @Override
    public boolean equals(Object other) {
        if (other == null || getClass() != other.getClass()) {
            return false;
        }

        Exemption that = (Exemption) other;
        return this.requested == that.requested
                && Objects.equals(this.type, that.type)
                && Objects.equals(this.outcome, that.outcome);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requested, type, outcome);
    }
}

