package uk.gov.pay.connector.chargeevent.model.domain;

import org.eclipse.persistence.descriptors.DescriptorEvent;
import org.eclipse.persistence.descriptors.DescriptorEventAdapter;
import org.eclipse.persistence.sessions.SessionEvent;
import org.eclipse.persistence.sessions.SessionEventAdapter;
import org.eclipse.persistence.sessions.SessionEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.service.ChargeService;

public class ChargeEventListener extends SessionEventAdapter implements SessionEventListener {
    private static final Logger logger = LoggerFactory.getLogger(ChargeEventListener.class);

    @Override
    public void postCommitTransaction(SessionEvent event) {
        logger.error("Charge event: {} ", event);
    }

    
}
