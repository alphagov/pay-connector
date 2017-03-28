package uk.gov.pay.connector.filters;

import com.google.common.base.Stopwatch;
import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;

public class LoggingFilter implements Filter {

    public static final String HEADER_REQUEST_ID = "X-Request-Id";
    private static final Logger logger = LoggerFactory.getLogger(LoggingFilter.class);

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

        Stopwatch stopwatch = Stopwatch.createStarted();

        String requestURL = ((HttpServletRequest) servletRequest).getRequestURI();
        String requestMethod = ((HttpServletRequest) servletRequest).getMethod();
        String requestId = StringUtils.defaultString(((HttpServletRequest) servletRequest).getHeader(HEADER_REQUEST_ID));

        MDC.put(HEADER_REQUEST_ID, requestId);

        logger.info(format("%s to %s began", requestMethod, requestURL));
        try {
            filterChain.doFilter(servletRequest, servletResponse);
        } catch (Throwable throwable) {
            logger.error("Exception - connector request - " + requestURL + " - exception - " + throwable.getMessage(), throwable);
        } finally {
            logger.info(format("%s to %s ended - total time %dms", requestMethod, requestURL,
                    stopwatch.elapsed(TimeUnit.MILLISECONDS)));
            stopwatch.stop();
        }
    }

    @Override
    public void destroy() {
        logger.warn("Destroying logging filter");
    }
}
