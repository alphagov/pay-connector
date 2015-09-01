package uk.gov.pay.connector.util;

import com.jayway.restassured.response.ValidatableResponse;

import static javax.ws.rs.HttpMethod.GET;
import static org.hamcrest.Matchers.is;

public class LinksAssert {
    public static void assertSelfLink(ValidatableResponse response, String selfHref) {
        response.body("links.find {links -> links.rel == 'self' }.href", is(selfHref));
        response.body("links.find {links -> links.rel == 'self' }.method", is(GET));
    }
}
