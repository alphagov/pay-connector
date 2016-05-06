package uk.gov.pay.connector.resources;

import black.door.hate.HalRepresentation;
import black.door.hate.HalRepresentation.HalRepresentationBuilder;
import black.door.hate.HalResource;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

public class HalResourceBuilder implements HalResource {
    public static final String SELF_LINK_KEY = "self";
    private Map<String, String> linkMap = new HashMap<>();
    private Map<String, Object> propertyMap = new HashMap<>();
    private String selfLink;

    public HalResourceBuilder withLink(String linkKey, String linkValue) {
        if (linkKey!=null && linkValue!=null) {
            linkMap.put(linkKey, linkValue);
        }
        return this;
    }

    public HalResourceBuilder withSelfLink(String selfLink) {
        if (selfLink != null) {
            this.selfLink = selfLink;
        }
        return this;
    }

    public HalResourceBuilder withProperty(String key, Object value) {
        if (key!=null && value!=null) {
            propertyMap.put(key, value);
        }
        return this;
    }

    @Override
    public URI location() {
        return buildUri(selfLink);
    }

    @Override
    public HalRepresentationBuilder representationBuilder() {
        HalRepresentationBuilder builder = HalRepresentation.builder();
        builder.addLink(SELF_LINK_KEY, this);

        for (String key : linkMap.keySet()) {
            builder.addLink(key, buildUri(linkMap.get(key)));
        }
        for (String key : propertyMap.keySet()) {
            builder.addProperty(key, propertyMap.get(key));
        }
        return builder;
    }

    public String build() {
        try {
            return representationBuilder().build().serialize();
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return null;
    }

    public URI buildUri(String uri) {
        try {
            return new URI(uri);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return null;
    }
}
