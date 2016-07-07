package uk.gov.pay.connector.dao;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.model.domain.EmailNotificationEntity;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.util.Optional;

@Transactional
public class EmailNotificationsDao extends JpaDao<EmailNotificationEntity> {

    @Inject
    public EmailNotificationsDao(Provider<EntityManager> entityManager) {
        super(entityManager);
    }

    public Optional<EmailNotificationEntity> findByAccountId(Long accountId) {
        return entityManager.get()
                .createQuery("SELECT e FROM EmailNotificationEntity e WHERE e.accountEntity.id = :accountId", EmailNotificationEntity.class)
                .setParameter("accountId", accountId)
                .getResultList().stream()
                .findFirst();
    }
}
