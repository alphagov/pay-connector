package uk.gov.pay.connector.model.builder;

import com.google.common.collect.ImmutableMap;
import uk.gov.pay.connector.model.ChargeResponse;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class AbstractRefundsResponseBuilder<T extends AbstractRefundsResponseBuilder<T, R>, R> {
    protected String createdDate;
    protected List<Map<String, Object>> links = new ArrayList<>();
    protected ChargeResponse.RefundSummary refundSummary;
    protected String refundId;

    protected abstract T thisObject();

    public T withRefundId(String refundId) {
        this.refundId = refundId;
        return thisObject();
    }
    
    public T withCreatedDate(String createdDate) {
        this.createdDate = createdDate;
        return thisObject();
    }

    public T withLink(String rel, String method, URI href) {
        links.add(ImmutableMap.of(
                "rel", rel,
                "method", method,
                "href", href
        ));
        return thisObject();
    }

    public T withLink(String rel, String method, URI href, String type, Map<String, Object> params) {
        links.add(ImmutableMap.of(
                "rel", rel,
                "method", method,
                "href", href,
                "type", type,
                "params", params
        ));

        return thisObject();
    }

    public T withRefunds(ChargeResponse.RefundSummary refundSummary) {
        this.refundSummary = refundSummary;
        return thisObject();
    }

    public abstract R build();
}
