package uk.gov.pay.connector.gateway.stripe.json;

import java.util.Optional;

public class StripeExpandableField<T extends StripeObjectWithId> {
    
    private String id;
    private T expandedObject;

    public StripeExpandableField(String id, T expandedObject) {
        this.id = id;
        this.expandedObject = expandedObject;
    }

    public boolean isExpanded() {
        return expandedObject != null;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Optional<T> getExpanded() {
        return Optional.ofNullable(expandedObject);
    }

    public void setExpanded(T expandedObject) {
        this.expandedObject = expandedObject;
    }
}
