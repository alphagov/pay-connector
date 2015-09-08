package uk.gov.pay.connector.util;

import com.google.common.collect.ImmutableMap;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.google.common.collect.Lists.newArrayList;
import static javax.ws.rs.HttpMethod.GET;

public class LinksBuilder {
    private final List<Link> links;

    private LinksBuilder() {
        links = new ArrayList<>();
    }

    public static LinksBuilder linksBuilder(URI selfLocation) {
        return new LinksBuilder()
                .addLink("self", GET, selfLocation);
    }

    public LinksBuilder addLink(String relation, String method, URI href) {
        links.add(new Link(relation, method, href));
        return this;
    }

    public Map<String, Object> appendLinksTo(Map<String, Object> data) {
        List<Map<String, Object>> dataLinks = newArrayList();
        for (Link link : links) {
            dataLinks.add(ImmutableMap.of("rel", link.rel, "method", link.method, "href", link.href));
        }
        data.put("links", dataLinks);
        return data;
    }

    private class Link {
        private String rel;
        private String method;
        private URI href;

        public Link(String rel, String method, URI href) {
            this.rel = rel;
            this.method = method;
            this.href = href;
        }
    }
}
