package uk.gov.pay.connector.dao;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.model.domain.RefundEntity;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.util.Optional;

@Transactional
public class RefundDao extends JpaDao<RefundEntity> {

    @Inject
    public RefundDao(final Provider<EntityManager> entityManager) {
        super(entityManager);
    }

    public Optional<RefundEntity> findById(long id) {
        return super.findById(RefundEntity.class, id);
    }

    public Optional<RefundEntity> findByExternalId(String externalId) {

        String query = "SELECT r FROM RefundEntity r " +
                "WHERE r.externalId = :externalId";

        return entityManager.get()
                .createQuery(query, RefundEntity.class)
                .setParameter("externalId", externalId)
                .getResultList().stream().findFirst();
    }
}
