package uk.gov.pay.connector.common.dao;

import org.eclipse.persistence.config.DescriptorCustomizer;
import org.eclipse.persistence.config.SessionCustomizer;
import org.eclipse.persistence.descriptors.ClassDescriptor;
import org.eclipse.persistence.descriptors.DescriptorEvent;
import org.eclipse.persistence.descriptors.DescriptorEventAdapter;
import org.eclipse.persistence.sessions.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.lang.annotation.Inherited;

public class TestThingy extends DescriptorEventAdapter implements SessionCustomizer, DescriptorCustomizer {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject 
    public TestThingy() {
                
    }

    @Override
    public void postInsert(DescriptorEvent event) {
        logger.info("postInsert {}", event);
    }

    /** This will audit a specific class. */
    public void customize(ClassDescriptor descriptor) throws Exception {
        descriptor.getEventManager().addListener(this);
    }

    /** This will audit all classes. */
    public void customize(Session session) throws Exception {
        for (ClassDescriptor descriptor : session.getDescriptors().values()) {
            customize(descriptor);
        }
    }
}
