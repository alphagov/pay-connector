package uk.gov.pay.connector.refund.model.builder;

import com.google.common.collect.ImmutableMap;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class AbstractRefundsResponseBuilder<T extends AbstractRefundsResponseBuilder<T, R>, R> {
    protected String createdDate;
    protected List<Map<String, Object>> dataLinks = new ArrayList<>();
    protected String refundId;
    protected RefundEntity refundEntity;
    protected String status;
    protected String extChargeId;
    protected Long amountSubmitted;

    protected abstract T thisObject();

    public T withRefundId(String refundId) {
        this.refundId = refundId;
        return thisObject();
    }

    public T withCreatedDate(String createdDate) {
        this.createdDate = createdDate;
        return thisObject();
    }

    public T withStatus(String status) {
        this.status = status;
        return thisObject();
    }

    public T withChargeId(String extChargeId) {
        this.extChargeId = extChargeId;
        return thisObject();
    }

    public T withAmountSubmitted(Long amountSubmitted) {
        this.amountSubmitted = amountSubmitted;
        return thisObject();
    }

    public T withLink(String rel, String method, URI href) {
        dataLinks.add(ImmutableMap.of(
                "rel", rel,
                "method", method,
                "href", href
        ));
        return thisObject();
    }

    public abstract R build();

}
