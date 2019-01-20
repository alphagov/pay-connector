package uk.gov.pay.connector.filters;

import com.google.common.base.Stopwatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;

public class RestClientLoggingFilter implements ClientRequestFilter, ClientResponseFilter {

    private static final Logger logger = LoggerFactory.getLogger(RestClientLoggingFilter.class);
    private static final String HEADER_REQUEST_ID = "X-Request-Id";

    private static ThreadLocal<String> requestId = new ThreadLocal<>();
    private static ThreadLocal<Stopwatch> timer = new ThreadLocal<>();

    @Override
    public void filter(ClientRequestContext requestContext) {
        timer.set(Stopwatch.createStarted());

        Optional<String> requestIdMaybe = Optional.ofNullable(MDC.get(HEADER_REQUEST_ID));
        requestId.set(requestIdMaybe.orElse(""));

        requestContext.getHeaders().add(HEADER_REQUEST_ID, requestId.get());

        logger.info(format("%s to %s began",
                requestContext.getMethod(),
                requestContext.getUri()));

    }

    @Override
    public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) {
        long elapsed = timer.get().elapsed(TimeUnit.MILLISECONDS);

        responseContext.getHeaders().add(HEADER_REQUEST_ID, requestId.get());

        logger.info(format("%s to %s ended - total time %dms",
                requestContext.getMethod(),
                requestContext.getUri(),
                elapsed));

        requestId.remove();
        timer.get().stop();
        timer.remove();
    }
}
