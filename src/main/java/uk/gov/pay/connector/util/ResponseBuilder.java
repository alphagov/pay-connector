package uk.gov.pay.connector.util;

import com.google.common.collect.ImmutableMap;

import java.net.URI;
import java.util.*;

public class ResponseBuilder {
    private Map<String, Object> charge;
    private Set<String> excludedFields;
    private List<Map<String, Object>> dataLinks;

    public ResponseBuilder() {
        excludedFields = new LinkedHashSet<>();
        dataLinks = new ArrayList();
    }

    public ResponseBuilder withLink(String rel, String method, URI href) {
        dataLinks.add(ImmutableMap.of(
                "rel", rel,
                "method", method,
                "href", href
        ));

        return this;
    }

    public ResponseBuilder withCharge(Map<String, Object> charge) {
        this.charge = charge;
        return this;
    }


    public ResponseBuilder withoutChargeField(String chargeFieldKey) {
        excludedFields.add(chargeFieldKey);
        return this;
    }

    public Map<String, Object> build() {
        Map<String, Object> result = new LinkedHashMap<>(charge);

        excludedFields
                .stream()
                .forEach(result::remove);

        result.put("links", dataLinks);

        return result;
    }
}
