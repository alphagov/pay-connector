package uk.gov.pay.connector.util;

import com.google.common.collect.ImmutableMap;
import uk.gov.pay.connector.model.api.ExternalChargeState;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class AbstractChargeResponseBuilder<T extends AbstractChargeResponseBuilder<T, R>, R> {
    protected String chargeId;
    protected Long amount;
    protected ExternalChargeState state;
    protected String gatewayTransactionId;
    protected String returnUrl;
    protected String description;
    protected ZonedDateTime createdDate;
    protected List<Map<String, Object>> links = new ArrayList<>();
    protected String reference;
    protected String providerName;

    protected abstract T thisObject();

    public T withChargeId(String chargeId) {
        this.chargeId = chargeId;
        return thisObject();
    }

    public T withAmount(Long amount) {
        this.amount = amount;
        return thisObject();
    }

    public T withState(ExternalChargeState state) {
        this.state = state;
        return thisObject();
    }

    public T withGatewayTransactionId(String gatewayTransactionId) {
        this.gatewayTransactionId = gatewayTransactionId;
        return thisObject();
    }

    public T withReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
        return thisObject();
    }

    public T withDescription(String description) {
        this.description = description;
        return thisObject();
    }

    public T withReference(String reference) {
        this.reference = reference;
        return thisObject();
    }

    public T withCreatedDate(ZonedDateTime createdDate) {
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

    public T withLink(String rel, String method, URI href, String type, Map<String,Object> params) {
        links.add(ImmutableMap.of(
                "rel", rel,
                "method", method,
                "href", href,
                "type", type,
                "params", params
        ));

        return thisObject();
    }

    public T withProviderName(String providerName) {
        this.providerName = providerName;
        return thisObject();
    }

    public abstract R build();
}
