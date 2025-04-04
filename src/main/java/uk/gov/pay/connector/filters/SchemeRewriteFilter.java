package uk.gov.pay.connector.filters;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import java.net.URI;

import static org.eclipse.jetty.http.HttpScheme.HTTPS;

@PreMatching
public class SchemeRewriteFilter implements ContainerRequestFilter {

    @Override
    public void filter(ContainerRequestContext request) {
        URI requestUri = request.getUriInfo().getRequestUriBuilder().scheme(HTTPS.asString()).build();
        URI baseUri = request.getUriInfo().getBaseUriBuilder().scheme(HTTPS.asString()).build();
        request.setRequestUri(baseUri, requestUri);
    }
}
