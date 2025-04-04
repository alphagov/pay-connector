package uk.gov.pay.connector.filters;

import org.glassfish.jersey.internal.PropertiesDelegate;
import org.glassfish.jersey.server.ContainerRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import jakarta.ws.rs.core.SecurityContext;
import java.net.URI;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

class SchemeRewriteFilterTest {

    private SchemeRewriteFilter schemeRewriteFilter = new SchemeRewriteFilter();

    @Test
    void filter_shouldRewriteSchemeInBaseUriAndRequestUriToHttps() {

        SecurityContext securityContextMock = Mockito.mock(SecurityContext.class);
        PropertiesDelegate propertiesDelegateMock = Mockito.mock(PropertiesDelegate.class);

        URI baseUri = URI.create("tcp://pay.gov.uk");
        URI requestUri = URI.create("tcp://pay.gov.uk/hey/you");

        ContainerRequest request = new ContainerRequest(baseUri, requestUri, "tcp", securityContextMock, propertiesDelegateMock);

        schemeRewriteFilter.filter(request);

        assertThat(request.getBaseUri(), is(URI.create("https://pay.gov.uk")));
        assertThat(request.getRequestUri(), is(URI.create("https://pay.gov.uk/hey/you")));
    }
}
