package uk.gov.pay.connector.util;

import org.eclipse.persistence.config.SessionCustomizer;
import org.eclipse.persistence.internal.sessions.AbstractSession;
import org.eclipse.persistence.queries.DatabaseQuery;
import org.eclipse.persistence.sessions.*;
import org.eclipse.persistence.tools.profiler.PerformanceMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConnectorSessionCustomiser implements SessionCustomizer {

    private static final int QUERY_RETRY_ATTEMPT_COUNT_ZERO_BASED_INDEX = 0;
    private static final int DELAY_BETWEEN_CONNECTION_ATTEMPTS_MILLIS = 2000;

    @Override
    public void customize(Session session) throws Exception {
        DatabaseLogin datasourceLogin = (DatabaseLogin) session.getDatasourceLogin();
        datasourceLogin.setQueryRetryAttemptCount(QUERY_RETRY_ATTEMPT_COUNT_ZERO_BASED_INDEX);
        datasourceLogin.setDelayBetweenConnectionAttempts(DELAY_BETWEEN_CONNECTION_ATTEMPTS_MILLIS);
        //session.setProfiler(new XRaySessionProfiler());
    }
}

