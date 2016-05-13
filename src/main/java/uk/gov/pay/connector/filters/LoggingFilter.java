package uk.gov.pay.connector.filters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

public class LoggingFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(LoggingFilter.class);

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {}

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        String requestURL = ((HttpServletRequest) servletRequest).getRequestURI();

        logger.info("Start - connector request - " + requestURL);
        try {
            filterChain.doFilter(servletRequest, servletResponse);
        } catch (Throwable throwable){
            logger.error("Exception - connector request - "+ requestURL + " - exception - "+ throwable.getMessage(), throwable);
        }
        finally {
            logger.info("End - connector request - " + requestURL);
        }
    }

    @Override
    public void destroy() {}
}
