package uk.gov.pay.connector.model.api;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

public class Link {

    private String href;
    private final String rel;
    private final String method;

    public static Link aLink(String href, String rel, String method) {
        return new Link(href, rel, method);
    }

    private Link(String href, String rel, String method) {
        this.href = href;
        this.rel = rel;
        this.method = method;
    }

    public Map<String, String> toMap() {
        return ImmutableMap.of(
                "href", href,
                "rel", rel,
                "method", method
        );
    }
}
