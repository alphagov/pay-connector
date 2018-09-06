package uk.gov.pay.connector.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.pay.connector.model.builder.AbstractRefundsResponseBuilder;

import java.net.URI;
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
            return new SearchRefundsResponse(refundId, refundSummary, createdDate, links);
        }
    }

    public static SearchRefundsResponseBuilder aAllRefundsResponseBuilder() {
        return new SearchRefundsResponseBuilder();
    }

    @JsonProperty("refund_id")
    private String refundId;
    
    @JsonProperty("refund_summary")
    private ChargeResponse.RefundSummary refundSummary;
    
    @JsonProperty("created_date")
    private String createdDate;

    @JsonProperty("links")
    private List<Map<String, Object>> dataLinks = new ArrayList<>();
    
    

    protected SearchRefundsResponse(String refundId, ChargeResponse.RefundSummary refundSummary,
                                    String createdDate, List<Map<String, Object>> dataLinks) {
        this.refundId= refundId;
        this.refundSummary = refundSummary;
        this.createdDate = createdDate;
        this.dataLinks = dataLinks;
    }

    public String getRefundId() {
        return refundId;
    }

    public ChargeResponse.RefundSummary getRefundSummary() {
        return refundSummary;
    }

    public URI getLink(String rel) {
        return dataLinks.stream()
                .filter(map -> rel.equals(map.get("rel")))
                .findFirst()
                .map(link -> (URI) link.get("href"))
                .get();
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
        if (dataLinks != null ? !dataLinks.equals(that.dataLinks) : that.dataLinks != null)
            return false;
        if (createdDate != null ? !createdDate.equals(that.createdDate) : that.createdDate != null)
            return false;
        return (refundSummary != null ? !refundSummary.equals(that.refundSummary) : that.refundSummary != null); 
    }

    @Override
    public int hashCode() {
        int result = dataLinks != null ? dataLinks.hashCode() : 0;
        result = 31 * result + (refundId != null ? refundId.hashCode() : 0);
        result = 31 * result + (createdDate != null ? createdDate.hashCode() : 0);
        result = 31 * result + (refundSummary != null ? refundSummary.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        // Some services put PII in the description, so don’t include it in the stringification
        return "SearchRefundsResponse{" +
                " refundId='" + refundId + '\'' +
                ", refundSummary=" + refundSummary +
                ", createdDate=" + createdDate +
                ", dataLinks=" + dataLinks +
                '}';
    }
}


