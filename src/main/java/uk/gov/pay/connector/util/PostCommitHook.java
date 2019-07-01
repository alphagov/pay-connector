package uk.gov.pay.connector.util;

import org.eclipse.persistence.internal.sessions.RepeatableWriteUnitOfWork;
import org.eclipse.persistence.sessions.Session;
import org.eclipse.persistence.sessions.SessionEvent;
import org.eclipse.persistence.sessions.SessionEventAdapter;

import java.util.Map;

public class PostCommitHook extends SessionEventAdapter {
    @Override
    public void preCommitUnitOfWork(SessionEvent event) {
        super.preCommitUnitOfWork(event);
    }

    @Override
    public void postCommitUnitOfWork(SessionEvent event) {
        final Session session = event.getSession();
        if (session.isUnitOfWork()) {
            final Map allChangeSets = session.acquireUnitOfWork().getCurrentChanges().getAllChangeSets();
            System.out.println(allChangeSets);
        }
    }

    @Override
    public void postCommitTransaction(SessionEvent event) {
//        throw new RuntimeException("Oh yes");
        System.out.println("postCommitTransaction: " + event);
    }
}
