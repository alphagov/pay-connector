package uk.gov.pay.connector.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.pay.connector.model.builder.AbstractRefundsResponseBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public class SearchRefundsResponse {

    public static class SearchRefundsResponseBuilder extends AbstractRefundsResponseBuilder<SearchRefundsResponseBuilder, SearchRefundsResponse> {
        @Override
        protected SearchRefundsResponseBuilder thisObject() {
            return this;
        }

        @Override
        public SearchRefundsResponse build() {
            return new SearchRefundsResponse(
                    refundId,
                    createdDate,
                    status,
                    extChargeId,
                    amountSubmitted,
                    dataLinks
            );
        }
    }

    public static SearchRefundsResponseBuilder anAllRefundsResponseBuilder() {
        return new SearchRefundsResponseBuilder();
    }

    @JsonProperty("refund_id")
    private String refundId;

    @JsonProperty("created_date")
    private String createdDate;

    @JsonProperty("status")
    private String status;

    @JsonProperty("charge_id")

    private String extChargeId;

    @JsonProperty("amount_submitted")
    private Long amountSubmitted;

    @JsonProperty("links")
    private List<Map<String, Object>> dataLinks = new ArrayList<>();

    protected SearchRefundsResponse(String refundId,
                                    String createdDate,
                                    String status,
                                    String extChargeId,
                                    Long amountSubmitted,
                                    List<Map<String, Object>> dataLinks) {
        this.refundId = refundId;
        this.createdDate = createdDate;
        this.status = status;
        this.extChargeId = extChargeId;
        this.amountSubmitted = amountSubmitted;
        this.dataLinks = dataLinks;
    }

    public String getRefundId() {
        return refundId;
    }


    public String getCreatedDate() {
        return createdDate;
    }

    public String getStatus() {
        return status;
    }

    public String getChargeId() {
        return extChargeId;
    }

    public Long getAmountSubmitted() {
        return amountSubmitted;
    }

    public List<Map<String, Object>> getDataLinks() {
        return dataLinks;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SearchRefundsResponse that = (SearchRefundsResponse) o;

        if (refundId != null ? !refundId.equals(that.refundId) : that.refundId != null)
            return false;
        if (createdDate != null ? !createdDate.equals(that.createdDate) : that.createdDate != null)
            return false;
        if (!status.equals(that.status)) {
            return false;
        }
        if (extChargeId != null ? !extChargeId.equals(that.extChargeId) : that.extChargeId != null)
            return false;
        if (dataLinks != null ? !dataLinks.equals(that.dataLinks) : that.dataLinks != null)
            return false;
        return amountSubmitted != null ? amountSubmitted.equals(that.amountSubmitted)
                : that.amountSubmitted == null;
    }

    @Override
    public int hashCode() {
        int result = dataLinks != null ? dataLinks.hashCode() : 0;
        result = 31 * result + (refundId != null ? refundId.hashCode() : 0);
        result = 31 * result + (createdDate != null ? createdDate.hashCode() : 0);
        result = 31 * result + (status != null ? status.hashCode() : 0);
        result = 31 * result + (extChargeId != null ? extChargeId.hashCode() : 0);
        result = 31 * result + (amountSubmitted != null ? amountSubmitted.hashCode() : 0);
        result = 31 * result + (dataLinks != null ? dataLinks.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        // Some services put PII in the description, so don’t include it in the stringification
        return "SearchRefundsResponse{" +
                " refundId='" + refundId + '\'' +
                ", createdDate=" + createdDate +
                ", status=" + status +
                ", amountSubmitted=" + amountSubmitted +
                ", dataLinks=" + dataLinks +
                '}';
    }
}


