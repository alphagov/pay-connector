package uk.gov.pay.connector.util;

import com.jayway.restassured.response.ValidatableResponse;

import static javax.ws.rs.HttpMethod.GET;
import static javax.ws.rs.HttpMethod.POST;
import static org.hamcrest.Matchers.is;

public class LinksAssert {
    public static void assertSelfLink(ValidatableResponse response, String selfHref) {
        assertLink(response, "self", GET, selfHref);
    }

    public static void assertNextUrlLink(ValidatableResponse response, String selfHref) {
        assertLink(response, "next_url", GET, selfHref);
    }

    public static void assertCardAuthLink(ValidatableResponse response, String href) {
        assertLink(response, "cardAuth", POST, href);
    }

    private static void assertLink(ValidatableResponse response, String rel, String method, String href) {
        response.body("links.find {link -> link.rel == '" + rel + "' }.href", is(href));
        response.body("links.find {link -> link.rel == '" + rel + "' }.method", is(method));
    }
}
