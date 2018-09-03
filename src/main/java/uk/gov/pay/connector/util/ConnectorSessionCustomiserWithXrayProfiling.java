package uk.gov.pay.connector.util;

import org.eclipse.persistence.config.SessionCustomizer;
import org.eclipse.persistence.sessions.Session;
import uk.gov.pay.commons.utils.xray.XRaySessionProfiler;

public class ConnectorSessionCustomiserWithXrayProfiling extends ConnectorSessionCustomiser implements SessionCustomizer {
    
    @Override
    public void customize(Session session) {
        session.setProfiler(new XRaySessionProfiler());
        super.customize(session);
    }
}
