package uk.gov.pay.connector.util;

import org.eclipse.persistence.sessions.DatabaseLogin;
import org.eclipse.persistence.sessions.Session;
import org.eclipse.persistence.sessions.SessionCustomizer;

public class ConnectorSessionCustomiser implements SessionCustomizer {

    private static final int QUERY_RETRY_ATTEMPT_COUNT_ZERO_BASED_INDEX = 0;
    private static final int DELAY_BETWEEN_CONNECTION_ATTEMPTS_MILLIS = 2000;

    @Override
    public void customize(Session session) {
        DatabaseLogin datasourceLogin = (DatabaseLogin) session.getDatasourceLogin();
        datasourceLogin.setQueryRetryAttemptCount(QUERY_RETRY_ATTEMPT_COUNT_ZERO_BASED_INDEX);
        datasourceLogin.setDelayBetweenConnectionAttempts(DELAY_BETWEEN_CONNECTION_ATTEMPTS_MILLIS);
    }
}

